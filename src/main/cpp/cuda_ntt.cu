#include "cuda_ntt.h"
#include "runtime/cuda_workspace_budget.h"

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include <cuda_runtime.h>

namespace bigmath::cuda {

// NTT-friendly primes = k*2^b + 1 with primitive root 3. CRT reconstructs
// exact coefficients over MOD1*MOD3 (~2^58.8).
// Every modulus is < 2^30, so a product of two reduced residues is < 2^60 and
// fits in u64 without 128-bit arithmetic.
namespace {

using u64 = uint64_t;

constexpr u64 MOD1 = 998244353;   // 119 * 2^23 + 1
constexpr u64 MOD3 = 469762049;   //   7 * 2^26 + 1
constexpr u64 PRIMITIVE_ROOT = 3;

constexpr u64 host_mulmod(u64 a, u64 b, u64 mod) {
	return (a % mod) * (b % mod) % mod;
}

constexpr u64 host_powmod(u64 a, u64 e, u64 mod) {
	u64 r = 1;
	while (e) {
		if (e & 1) r = host_mulmod(r, a, mod);
		a = host_mulmod(a, a, mod);
		e >>= 1;
	}
	return r;
}

constexpr u64 MOD1_INV_MOD3 = host_powmod(MOD1, MOD3 - 2, MOD3);

// Barrett constant mu = floor(2^64 / p). For an odd prime p, (~0ULL)/p equals
// floor(2^64 / p) exactly (p never divides 2^64).
constexpr u64 barrett_mu(u64 p) { return (~static_cast<u64>(0)) / p; }
constexpr u64 MU1 = barrett_mu(MOD1);
constexpr u64 MU3 = barrett_mu(MOD3);

// Barrett reduction of a*b mod p for a,b < p < 2^30 (so a*b < 2^60 < 2^64).
// Replaces the slow 64-bit hardware modulo on the hottest NTT path. q is
// floor(x*mu / 2^64) and underestimates floor(x/p) by at most 1, so at most two
// conditional subtractions restore the canonical residue.
__device__ __forceinline__ u64 dmul(u64 a, u64 b, u64 p, u64 mu) {
	const u64 x = a * b;                 // < 2^60
	const u64 q = __umul64hi(x, mu);     // ~ x / p
	u64 r = x - q * p;
	if (r >= p) r -= p;
	if (r >= p) r -= p;
	return r;
}

__global__ void k_bit_reverse(u64 *a, int n, int logn) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) return;
	// reverse the low logn bits of i
	unsigned x = static_cast<unsigned>(i);
	x = (x >> 16) | (x << 16);
	x = ((x & 0x00ff00ffu) << 8) | ((x & 0xff00ff00u) >> 8);
	x = ((x & 0x0f0f0f0fu) << 4) | ((x & 0xf0f0f0f0u) >> 4);
	x = ((x & 0x33333333u) << 2) | ((x & 0xccccccccu) >> 2);
	x = ((x & 0x55555555u) << 1) | ((x & 0xaaaaaaaau) >> 1);
	const int j = static_cast<int>(x >> (32 - logn));
	if (i < j) {
		const u64 t = a[i];
		a[i] = a[j];
		a[j] = t;
	}
}

// One Cooley-Tukey stage. `twiddles` holds powers of the primitive n-th root
// (forward or inverse); stage `len` reads index j*(n/len).
__global__ void k_butterfly(u64 *a, const u64 *twiddles, int n, int len, u64 p, u64 mu) {
	const int t = blockIdx.x * blockDim.x + threadIdx.x;
	const int half = len >> 1;
	if (t >= (n >> 1)) return;
	const int block = t / half;
	const int j = t - block * half;
	const int i = block * len + j;
	const int step = n / len;
	const u64 u = a[i];
	const u64 v = dmul(a[i + half], twiddles[j * step], p, mu);
	u64 s = u + v;
	if (s >= p) s -= p;
	u64 d = u >= v ? u - v : u + p - v;
	a[i] = s;
	a[i + half] = d;
}

// Fused shared-memory stages. Each block owns one `tile`-element span of the
// (already bit-reversed) array and runs every Cooley-Tukey stage with len <=
// tile entirely in shared memory, so those ~log2(tile) stages cost a single
// global read + write instead of one full global pass each. Twiddle indices
// (j * n/len) are identical to the global k_butterfly because the span is
// tile-aligned and tile is a multiple of len, so the same global twiddle table
// is reused. Launch with blockDim = tile/2 threads and tile*sizeof(u64) smem.
__global__ void k_ntt_shared(u64 *a, const u64 *twiddles, int n, int tile, u64 p, u64 mu) {
	extern __shared__ u64 s[];
	const int base = blockIdx.x * tile;
	const int tid = threadIdx.x;          // 0 .. tile/2 - 1
	const int halfTile = tile >> 1;
	s[tid] = a[base + tid];
	s[tid + halfTile] = a[base + tid + halfTile];
	__syncthreads();
	for (int len = 2; len <= tile; len <<= 1) {
		const int half = len >> 1;
		const int step = n / len;
		const int block = tid / half;
		const int j = tid - block * half;
		const int i = block * len + j;
		const u64 u = s[i];
		const u64 v = dmul(s[i + half], twiddles[j * step], p, mu);
		u64 sp = u + v;
		if (sp >= p) sp -= p;
		s[i] = sp;
		s[i + half] = u >= v ? u - v : u + p - v;
		__syncthreads();
	}
	a[base + tid] = s[tid];
	a[base + tid + halfTile] = s[tid + halfTile];
}

__global__ void k_pointwise(u64 *fa, const u64 *fb, int n, u64 p, u64 mu) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) return;
	fa[i] = dmul(fa[i], fb[i], p, mu);
}

__global__ void k_scale(u64 *a, int n, u64 inv_n, u64 p, u64 mu) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) return;
	a[i] = dmul(a[i], inv_n, p, mu);
}

// out[i] = CRT(c1[i] mod MOD1, c3[i] mod MOD3)  in [0, MOD1*MOD3)
__global__ void k_crt(const u64 *c1, const u64 *c3, u64 *out, int count, u64 mu3) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= count) return;
	const u64 r1 = c1[i];
	const u64 r3 = c3[i];
	const u64 r1m = r1 % MOD3;
	const u64 delta = r3 >= r1m ? r3 - r1m : r3 + MOD3 - r1m;
	out[i] = r1 + dmul(delta, MOD1_INV_MOD3, MOD3, mu3) * MOD1;
}

struct NttWorkspace {
	int capacity = 0;          // allocated element count (n)
	cudaStream_t stream = nullptr;
	u64 *fa = nullptr;         // working buffer A
	u64 *fb = nullptr;         // working buffer B
	u64 *c1 = nullptr;         // residues from the first prime
	u64 *out = nullptr;        // CRT result
	// Per-prime forward/inverse twiddles (n/2 each), cached across calls.
	u64 *wf1 = nullptr;        // MOD1 forward twiddles
	u64 *wi1 = nullptr;        // MOD1 inverse twiddles
	u64 *wf3 = nullptr;        // MOD3 forward twiddles
	u64 *wi3 = nullptr;        // MOD3 inverse twiddles
	int twiddle_n = 0;         // transform size the cached twiddles are valid for
	u64 *host_w = nullptr;     // pinned staging for twiddle upload
	u64 *host_a = nullptr;     // pinned H2D staging for left digits
	u64 *host_b = nullptr;     // pinned H2D staging for right digits
	u64 *host_out = nullptr;   // pinned D2H result staging
	int host_w_capacity = 0;
	int host_input_capacity = 0;
	int host_out_capacity = 0;
	size_t device_bytes = 0;

	~NttWorkspace() { release(); }

	void release() {
		cudaFree(fa); cudaFree(fb); cudaFree(c1); cudaFree(out);
		cudaFree(wf1); cudaFree(wi1); cudaFree(wf3); cudaFree(wi3);
		fa = fb = c1 = out = wf1 = wi1 = wf3 = wi3 = nullptr;
		cudaFreeHost(host_w);
		cudaFreeHost(host_a);
		cudaFreeHost(host_b);
		cudaFreeHost(host_out);
		host_w = host_a = host_b = host_out = nullptr;
		host_w_capacity = 0;
		host_input_capacity = 0;
		host_out_capacity = 0;
		if (stream != nullptr) {
			cudaStreamDestroy(stream);
			stream = nullptr;
		}
		capacity = 0;
		twiddle_n = 0;
		device_bytes = 0;
	}

	bool ensure(int n) {
		if (capacity >= n) return true;
		release();
		if (cudaStreamCreateWithFlags(&stream, cudaStreamNonBlocking) != cudaSuccess) return false;
		const size_t full = sizeof(u64) * static_cast<size_t>(n);
		const size_t half = sizeof(u64) * static_cast<size_t>(n / 2);
		if (cudaMalloc(&fa, full) != cudaSuccess ||
				cudaMalloc(&fb, full) != cudaSuccess ||
				cudaMalloc(&c1, full) != cudaSuccess ||
				cudaMalloc(&out, full) != cudaSuccess ||
				cudaMalloc(&wf1, half) != cudaSuccess ||
				cudaMalloc(&wi1, half) != cudaSuccess ||
				cudaMalloc(&wf3, half) != cudaSuccess ||
				cudaMalloc(&wi3, half) != cudaSuccess) {
			release();
			return false;
		}
		capacity = n;
		device_bytes = full * 4 + half * 4;
		return true;
	}

	bool ensure_host_buffers(int n) {
		const int half = n / 2;
		if (host_w_capacity < half) {
			cudaFreeHost(host_w);
			host_w = nullptr;
			host_w_capacity = 0;
			if (cudaHostAlloc(
					reinterpret_cast<void **>(&host_w),
					sizeof(u64) * static_cast<size_t>(half),
					cudaHostAllocDefault
			) != cudaSuccess) return false;
			host_w_capacity = half;
		}
		if (host_input_capacity < n) {
			cudaFreeHost(host_a);
			cudaFreeHost(host_b);
			host_a = nullptr;
			host_b = nullptr;
			host_input_capacity = 0;
			if (cudaHostAlloc(
					reinterpret_cast<void **>(&host_a),
					sizeof(u64) * static_cast<size_t>(n),
					cudaHostAllocDefault
			) != cudaSuccess || cudaHostAlloc(
					reinterpret_cast<void **>(&host_b),
					sizeof(u64) * static_cast<size_t>(n),
					cudaHostAllocDefault
			) != cudaSuccess) {
				cudaFreeHost(host_a);
				cudaFreeHost(host_b);
				host_a = nullptr;
				host_b = nullptr;
				return false;
			}
			host_input_capacity = n;
		}
		if (host_out_capacity < n) {
			cudaFreeHost(host_out);
			host_out = nullptr;
			host_out_capacity = 0;
			if (cudaHostAlloc(
					reinterpret_cast<void **>(&host_out),
					sizeof(u64) * static_cast<size_t>(n),
					cudaHostAllocDefault
			) != cudaSuccess) return false;
			host_out_capacity = n;
		}
		return true;
	}
};

bool ensure_twiddles(NttWorkspace &workspace, int n);

class NttWorkspacePool {
private:
	struct Slot {
		std::unique_ptr<NttWorkspace> workspace;
		int transform_size = 0;
		uint64_t device_bytes = 0;
		bool busy = false;
		uint64_t last_used = 0;
	};

public:
	class Lease {
	public:
		Lease() = default;
		Lease(NttWorkspacePool *pool, Slot *slot) : pool_(pool), slot_(slot) {}
		~Lease() { release(); }

		Lease(const Lease &) = delete;
		Lease &operator=(const Lease &) = delete;
		Lease(Lease &&other) noexcept : pool_(other.pool_), slot_(other.slot_) {
			other.pool_ = nullptr;
		}
		Lease &operator=(Lease &&other) noexcept {
			if (this != &other) {
				release();
				pool_ = other.pool_;
				slot_ = other.slot_;
				other.pool_ = nullptr;
			}
			return *this;
		}

		explicit operator bool() const { return pool_ != nullptr; }
		NttWorkspace &workspace() { return *slot_->workspace; }

	private:
		void release() {
			if (pool_ != nullptr) {
				pool_->release(slot_);
				pool_ = nullptr;
			}
		}

		NttWorkspacePool *pool_ = nullptr;
		Slot *slot_ = nullptr;
	};

	bool configure(int device) {
		std::lock_guard lock(mutex_);
		if (reserved_bytes_ != 0) return false;
		for (const auto &slot : slots_) {
			if (slot->busy) return false;
		}
		for (const auto &slot : slots_) {
			bigmath::runtime::release_cuda_workspace_bytes(device_, slot->device_bytes);
		}
		slots_.clear();
		allocated_bytes_ = 0;
		device_ = device;
		configured_ = true;
		return true;
	}

	Lease acquire(int n, uint64_t max_wait_nanos) {
		const auto deadline = std::chrono::steady_clock::now() +
			std::chrono::nanoseconds(max_wait_nanos);
		const uint64_t required = static_cast<uint64_t>(sizeof(u64)) *
			static_cast<uint64_t>(n) * 6;
		for (;;) {
			std::unique_lock lock(mutex_);
			if (!configured_) return Lease{};
			for (const auto &slot : slots_) {
				if (!slot->busy && slot->transform_size == n) {
					slot->busy = true;
					slot->last_used = next_tick();
					if (cudaSetDevice(device_) != cudaSuccess) {
						slot->busy = false;
						return Lease{};
					}
					return Lease(this, slot.get());
				}
			}

			if (bigmath::runtime::reserve_cuda_workspace_bytes(device_, required)) {
				reserved_bytes_ += required;
				const int device = device_;
				lock.unlock();
				auto workspace = std::make_unique<NttWorkspace>();
				const bool initialized = cudaSetDevice(device) == cudaSuccess &&
					workspace->ensure(n) && workspace->ensure_host_buffers(n) &&
					ensure_twiddles(*workspace, n);
				lock.lock();
				reserved_bytes_ -= required;
				if (!initialized) {
					bigmath::runtime::release_cuda_workspace_bytes(device_, required);
					lock.unlock();
					workspace.reset();
					lock.lock();
					condition_.notify_all();
					return Lease{};
				}
				auto slot = std::make_unique<Slot>();
				slot->device_bytes = workspace->device_bytes;
				slot->transform_size = n;
				slot->workspace = std::move(workspace);
				slot->busy = true;
				slot->last_used = next_tick();
				allocated_bytes_ += required;
				Slot *acquired = slot.get();
				slots_.push_back(std::move(slot));
				return Lease(this, acquired);
			}

			if (evict_idle_lru()) continue;
			if (max_wait_nanos == 0 ||
					condition_.wait_until(lock, deadline) == std::cv_status::timeout) {
				return Lease{};
			}
		}
	}

	uint64_t in_use_bytes() const {
		std::lock_guard lock(mutex_);
		uint64_t bytes = 0;
		for (const auto &slot : slots_) if (slot->busy) bytes += slot->device_bytes;
		return bytes;
	}

	int capacity() const {
		std::lock_guard lock(mutex_);
		return static_cast<int>(slots_.size());
	}

	int in_use() const {
		std::lock_guard lock(mutex_);
		return static_cast<int>(std::count_if(slots_.begin(), slots_.end(), [](const auto &slot) {
			return slot->busy;
		}));
	}

	bool available(int transform_size) const {
		std::lock_guard lock(mutex_);
		for (const auto &slot : slots_) {
			if (!slot->busy && slot->transform_size == transform_size) return true;
		}
		const uint64_t required = static_cast<uint64_t>(sizeof(u64)) *
			static_cast<uint64_t>(transform_size) * 6;
		const uint64_t budget = bigmath::runtime::cuda_workspace_budget_bytes(device_);
		const uint64_t allocated = bigmath::runtime::cuda_workspace_allocated_bytes(device_);
		return configured_ && required <= budget && allocated <= budget - required;
	}

private:
	uint64_t next_tick() {
		if (++tick_ == 0) tick_ = 1;
		return tick_;
	}

	bool evict_idle_lru() {
		size_t target = slots_.size();
		for (size_t index = 0; index < slots_.size(); index++) {
			if (slots_[index]->busy) continue;
			if (target == slots_.size() || slots_[index]->last_used < slots_[target]->last_used) {
				target = index;
			}
		}
		if (target == slots_.size()) return false;
		allocated_bytes_ -= slots_[target]->device_bytes;
		bigmath::runtime::release_cuda_workspace_bytes(device_, slots_[target]->device_bytes);
		slots_.erase(slots_.begin() + static_cast<std::ptrdiff_t>(target));
		return true;
	}

	void release(Slot *slot) {
		cudaError_t synchronized = cudaErrorInvalidResourceHandle;
		if (slot != nullptr && cudaSetDevice(device_) == cudaSuccess) {
			synchronized = cudaStreamSynchronize(slot->workspace->stream);
		}
		std::lock_guard lock(mutex_);
		if (slot != nullptr && synchronized == cudaSuccess) {
			slot->busy = false;
			slot->last_used = next_tick();
		} else if (slot != nullptr) {
			for (auto iterator = slots_.begin(); iterator != slots_.end(); ++iterator) {
				if (iterator->get() == slot) {
					allocated_bytes_ -= slot->device_bytes;
					bigmath::runtime::release_cuda_workspace_bytes(device_, slot->device_bytes);
					slots_.erase(iterator);
					break;
				}
			}
		}
		condition_.notify_one();
	}

	mutable std::mutex mutex_;
	std::condition_variable condition_;
	std::vector<std::unique_ptr<Slot>> slots_;
	bool configured_ = false;
	int device_ = 0;
	uint64_t allocated_bytes_ = 0;
	uint64_t reserved_bytes_ = 0;
	uint64_t tick_ = 0;
};

NttWorkspacePool &ntt_workspace_pool() {
	static NttWorkspacePool pool;
	return pool;
}

bool next_pow2(size_t value, int &out_n, int &out_logn) {
	if (value > static_cast<size_t>(NTT_MAX_TRANSFORM_SIZE)) return false;
	int p = 1, logn = 0;
	while (static_cast<size_t>(p) < value) {
		p <<= 1;
		logn++;
	}
	out_n = p;
	out_logn = logn;
	return true;
}

// Fill `host_w` with powers of the primitive n-th root of unity mod p and
// upload to `dst` (n/2 entries). `inverse` uses the inverse root.
bool upload_twiddles(NttWorkspace &ws, u64 *dst, int n, u64 p, bool inverse) {
	const u64 root = host_powmod(PRIMITIVE_ROOT, (p - 1) / static_cast<u64>(n), p);
	const u64 g = inverse ? host_powmod(root, p - 2, p) : root;
	const int half = n / 2;
	u64 cur = 1;
	for (int i = 0; i < half; i++) {
		ws.host_w[i] = cur;
		cur = host_mulmod(cur, g, p);
	}
	return cudaMemcpyAsync(
		dst,
		ws.host_w,
		sizeof(u64) * static_cast<size_t>(half),
		cudaMemcpyHostToDevice,
		ws.stream
	) == cudaSuccess && cudaStreamSynchronize(ws.stream) == cudaSuccess;
}

// Compute and upload all four twiddle tables (forward+inverse for both primes)
// once per transform size. Subsequent calls at the same `n` are a no-op, so the
// host-side n/2 sequential modmuls per table no longer run on every multiply.
bool ensure_twiddles(NttWorkspace &ws, int n) {
	if (ws.twiddle_n == n) {
		return true;
	}
	if (!upload_twiddles(ws, ws.wf1, n, MOD1, false) ||
			!upload_twiddles(ws, ws.wi1, n, MOD1, true) ||
			!upload_twiddles(ws, ws.wf3, n, MOD3, false) ||
			!upload_twiddles(ws, ws.wi3, n, MOD3, true)) {
		ws.twiddle_n = 0;
		return false;
	}
	ws.twiddle_n = n;
	return true;
}

bool load_digits(
		NttWorkspace &ws,
		u64 *host,
		u64 *dst,
		const std::vector<uint16_t> &digits,
		int n
) {
	for (size_t i = 0; i < digits.size(); i++) {
		host[i] = digits[i];
	}
	if (cudaMemcpyAsync(
			dst,
			host,
			sizeof(u64) * digits.size(),
			cudaMemcpyHostToDevice,
			ws.stream
	) != cudaSuccess) return false;
	return digits.size() >= static_cast<size_t>(n) || cudaMemsetAsync(
		dst + digits.size(),
		0,
		sizeof(u64) * (static_cast<size_t>(n) - digits.size()),
		ws.stream
	) == cudaSuccess;
}

constexpr int BLOCK = 256;
// Largest fused shared-memory tile. tile/2 threads/block (<= 1024) and
// tile*8 bytes of shared memory (16 KiB at 2048); fuses log2(tile)=11 stages.
constexpr int SHARED_TILE = 2048;

// Run all butterfly stages for one transform: the low stages (len <= tile)
// fused in shared memory, the remaining high-stride stages as global passes.
void run_stages(u64 *a, const u64 *tw, int n, u64 p, u64 mu, cudaStream_t stream) {
	const int tile = n < SHARED_TILE ? n : SHARED_TILE;
	k_ntt_shared<<<n / tile, tile / 2, sizeof(u64) * static_cast<size_t>(tile), stream>>>(
			a, tw, n, tile, p, mu);
	const int butter = (n / 2 + BLOCK - 1) / BLOCK;
	for (int len = 2 * tile; len <= n; len <<= 1) {
		k_butterfly<<<butter, BLOCK, 0, stream>>>(a, tw, n, len, p, mu);
	}
}

bool forward_ntt(u64 *a, const u64 *wf, int n, int logn, u64 p, u64 mu, cudaStream_t stream) {
	k_bit_reverse<<<(n + BLOCK - 1) / BLOCK, BLOCK, 0, stream>>>(a, n, logn);
	run_stages(a, wf, n, p, mu, stream);
	return cudaGetLastError() == cudaSuccess;
}

bool inverse_ntt(u64 *a, const u64 *wi, int n, int logn, u64 p, u64 mu, cudaStream_t stream) {
	k_bit_reverse<<<(n + BLOCK - 1) / BLOCK, BLOCK, 0, stream>>>(a, n, logn);
	run_stages(a, wi, n, p, mu, stream);
	const u64 inv_n = host_powmod(static_cast<u64>(n), p - 2, p);
	k_scale<<<(n + BLOCK - 1) / BLOCK, BLOCK, 0, stream>>>(a, n, inv_n, p, mu);
	return cudaGetLastError() == cudaSuccess;
}

// fa <- IDFT( DFT(a) .* DFT(b) ) mod p, results left in workspace.fa.
// Twiddles must already be uploaded for this n (see ensure_twiddles).
bool convolve_one_prime(NttWorkspace &ws, const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b, int n, int logn, u64 p, u64 mu,
		const u64 *wf, const u64 *wi) {
	// Squaring (a is b, e.g. the heart of modpow): the transform of b equals the
	// transform of a, so skip the redundant load + forward NTT and square fa in
	// place. Saves one of the two forward transforms per prime.
	const bool squaring = (&a == &b);
	if (!load_digits(ws, ws.host_a, ws.fa, a, n)) {
		return false;
	}
	if (!squaring && !load_digits(ws, ws.host_b, ws.fb, b, n)) {
		return false;
	}
	if (!forward_ntt(ws.fa, wf, n, logn, p, mu, ws.stream)) {
		return false;
	}
	if (!squaring && !forward_ntt(ws.fb, wf, n, logn, p, mu, ws.stream)) {
		return false;
	}
	const u64 *rhs = squaring ? ws.fa : ws.fb;
	k_pointwise<<<(n + BLOCK - 1) / BLOCK, BLOCK, 0, ws.stream>>>(ws.fa, rhs, n, p, mu);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	return inverse_ntt(ws.fa, wi, n, logn, p, mu, ws.stream) &&
		cudaStreamSynchronize(ws.stream) == cudaSuccess;
}

} // namespace

bool convolve_ntt_u16_to_limbs(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits,
		uint64_t max_queue_wait_nanos) {
	if (bits_per_digit != 16 || limb_bits == 0 || limb_bits > 64 || limb_bits % 16 != 0) {
		return false;
	}
	if (a.empty() || b.empty()) {
		out.clear();
		return true;
	}

	const size_t result_size = a.size() + b.size() - 1;
	int n = 0, logn = 0;
	if (!next_pow2(result_size, n, logn)) {
		return false;
	}

	// Exact-CRT bound: min_len * (base-1)^2 < MOD1*MOD3.
	static constexpr u64 CRT_MODULUS = MOD1 * MOD3;
	const u64 max_digit = 65535;
	const u64 min_len = static_cast<u64>(a.size() < b.size() ? a.size() : b.size());
	if (max_digit * max_digit > CRT_MODULUS / min_len) {
		return false;
	}

	NttWorkspacePool::Lease lease = ntt_workspace_pool().acquire(n, max_queue_wait_nanos);
	if (!lease) return false;
	NttWorkspace &ws = lease.workspace();

	// Prime 1 -> c1, prime 3 -> fa, then CRT into out.
	if (!convolve_one_prime(ws, a, b, n, logn, MOD1, MU1, ws.wf1, ws.wi1)) {
		return false;
	}
	if (cudaMemcpyAsync(
			ws.c1,
			ws.fa,
			sizeof(u64) * static_cast<size_t>(n),
			cudaMemcpyDeviceToDevice,
			ws.stream
	) != cudaSuccess) {
		return false;
	}
	if (!convolve_one_prime(ws, a, b, n, logn, MOD3, MU3, ws.wf3, ws.wi3)) {
		return false;
	}
	k_crt<<<(static_cast<int>(result_size) + BLOCK - 1) / BLOCK, BLOCK, 0, ws.stream>>>(
			ws.c1, ws.fa, ws.out, static_cast<int>(result_size), MU3);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}

	if (cudaMemcpyAsync(
			ws.host_out,
			ws.out,
			sizeof(u64) * result_size,
			cudaMemcpyDeviceToHost,
			ws.stream
	) != cudaSuccess || cudaStreamSynchronize(ws.stream) != cudaSuccess) {
		return false;
	}

	// Carry-propagate base-2^16 coefficients into limb_bits-wide limbs.
	const int digits_per_limb = static_cast<int>(limb_bits / 16);
	out.clear();
	out.reserve(result_size / static_cast<size_t>(digits_per_limb) + 2);
	u64 carry = 0, limb = 0;
	int limb_digit = 0;
	auto append = [&](u64 digit) {
		limb |= digit << (16 * limb_digit);
		if (++limb_digit == digits_per_limb) {
			out.push_back(limb);
			limb = 0;
			limb_digit = 0;
		}
	};
	for (size_t i = 0; i < result_size; i++) {
		const u64 value = ws.host_out[i] + carry;
		append(value & 0xffffu);
		carry = value >> 16;
	}
	while (carry != 0) {
		append(carry & 0xffffu);
		carry >>= 16;
	}
	if (limb_digit != 0) {
		out.push_back(limb);
	}
	while (!out.empty() && out.back() == 0) {
		out.pop_back();
	}
	return true;
}

bool configure_ntt_workspace_pool(int device) {
	if (device < 0 || cudaSetDevice(device) != cudaSuccess) return false;
	return ntt_workspace_pool().configure(device);
}

uint64_t ntt_workspace_in_use_bytes() {
	return ntt_workspace_pool().in_use_bytes();
}

int ntt_workspace_capacity() {
	return ntt_workspace_pool().capacity();
}

int ntt_workspace_in_use() {
	return ntt_workspace_pool().in_use();
}

bool ntt_workspace_available(int transform_size) {
	return ntt_workspace_pool().available(transform_size);
}

}
