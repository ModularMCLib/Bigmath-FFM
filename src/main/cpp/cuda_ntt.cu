#include "cuda_ntt.h"

#include <cstdint>
#include <cstring>
#include <vector>

#include <cuda_runtime.h>

namespace bigmath::cuda {

// NTT-friendly primes = k*2^b + 1 with primitive root 3, shared with the CPU
// path (ntt.h). CRT reconstructs exact coefficients over MOD1*MOD3 (~2^58.8).
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

__device__ __forceinline__ u64 dmul(u64 a, u64 b, u64 p) {
	return a * b % p;       // a,b < p < 2^30  =>  a*b < 2^60
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
__global__ void k_butterfly(u64 *a, const u64 *twiddles, int n, int len, u64 p) {
	const int t = blockIdx.x * blockDim.x + threadIdx.x;
	const int half = len >> 1;
	if (t >= (n >> 1)) return;
	const int block = t / half;
	const int j = t - block * half;
	const int i = block * len + j;
	const int step = n / len;
	const u64 u = a[i];
	const u64 v = dmul(a[i + half], twiddles[j * step], p);
	u64 s = u + v;
	if (s >= p) s -= p;
	u64 d = u >= v ? u - v : u + p - v;
	a[i] = s;
	a[i + half] = d;
}

__global__ void k_pointwise(u64 *fa, const u64 *fb, int n, u64 p) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) return;
	fa[i] = dmul(fa[i], fb[i], p);
}

__global__ void k_scale(u64 *a, int n, u64 inv_n, u64 p) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) return;
	a[i] = dmul(a[i], inv_n, p);
}

// out[i] = CRT(c1[i] mod MOD1, c3[i] mod MOD3)  in [0, MOD1*MOD3)
__global__ void k_crt(const u64 *c1, const u64 *c3, u64 *out, int count) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= count) return;
	const u64 r1 = c1[i];
	const u64 r3 = c3[i];
	const u64 r1m = r1 % MOD3;
	const u64 delta = r3 >= r1m ? r3 - r1m : r3 + MOD3 - r1m;
	out[i] = r1 + dmul(delta, MOD1_INV_MOD3, MOD3) * MOD1;
}

struct NttWorkspace {
	int capacity = 0;          // allocated element count (n)
	u64 *fa = nullptr;         // working buffer A
	u64 *fb = nullptr;         // working buffer B
	u64 *c1 = nullptr;         // residues from the first prime
	u64 *out = nullptr;        // CRT result
	u64 *wf = nullptr;         // forward twiddles (n/2)
	u64 *wi = nullptr;         // inverse twiddles (n/2)
	std::vector<u64> host_w;   // staging for twiddle upload
	std::vector<u64> host_in;  // staging for digit upload (zero-padded)
	std::vector<u64> host_out; // download buffer

	~NttWorkspace() { release(); }

	void release() {
		cudaFree(fa); cudaFree(fb); cudaFree(c1); cudaFree(out);
		cudaFree(wf); cudaFree(wi);
		fa = fb = c1 = out = wf = wi = nullptr;
		capacity = 0;
	}

	bool ensure(int n) {
		if (capacity >= n) return true;
		release();
		const size_t full = sizeof(u64) * static_cast<size_t>(n);
		const size_t half = sizeof(u64) * static_cast<size_t>(n / 2);
		if (cudaMalloc(&fa, full) != cudaSuccess ||
				cudaMalloc(&fb, full) != cudaSuccess ||
				cudaMalloc(&c1, full) != cudaSuccess ||
				cudaMalloc(&out, full) != cudaSuccess ||
				cudaMalloc(&wf, half) != cudaSuccess ||
				cudaMalloc(&wi, half) != cudaSuccess) {
			release();
			return false;
		}
		capacity = n;
		return true;
	}
};

bool next_pow2(size_t value, int &out_n, int &out_logn) {
	int p = 1, logn = 0;
	while (static_cast<size_t>(p) < value) {
		if (p > (1 << 27)) return false;   // keep within configured root order
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
	ws.host_w.resize(half);
	u64 cur = 1;
	for (int i = 0; i < half; i++) {
		ws.host_w[i] = cur;
		cur = host_mulmod(cur, g, p);
	}
	return cudaMemcpy(dst, ws.host_w.data(), sizeof(u64) * half, cudaMemcpyHostToDevice) == cudaSuccess;
}

bool load_digits(NttWorkspace &ws, u64 *dst, const std::vector<uint16_t> &digits, int n) {
	ws.host_in.assign(static_cast<size_t>(n), 0);
	for (size_t i = 0; i < digits.size(); i++) {
		ws.host_in[i] = digits[i];          // 0..65535 < every modulus
	}
	return cudaMemcpy(dst, ws.host_in.data(), sizeof(u64) * static_cast<size_t>(n),
			cudaMemcpyHostToDevice) == cudaSuccess;
}

constexpr int BLOCK = 256;

bool forward_ntt(u64 *a, const u64 *wf, int n, int logn, u64 p) {
	k_bit_reverse<<<(n + BLOCK - 1) / BLOCK, BLOCK>>>(a, n, logn);
	const int butter = (n / 2 + BLOCK - 1) / BLOCK;
	for (int len = 2; len <= n; len <<= 1) {
		k_butterfly<<<butter, BLOCK>>>(a, wf, n, len, p);
	}
	return cudaGetLastError() == cudaSuccess;
}

bool inverse_ntt(u64 *a, const u64 *wi, int n, int logn, u64 p) {
	k_bit_reverse<<<(n + BLOCK - 1) / BLOCK, BLOCK>>>(a, n, logn);
	const int butter = (n / 2 + BLOCK - 1) / BLOCK;
	for (int len = 2; len <= n; len <<= 1) {
		k_butterfly<<<butter, BLOCK>>>(a, wi, n, len, p);
	}
	const u64 inv_n = host_powmod(static_cast<u64>(n), p - 2, p);
	k_scale<<<(n + BLOCK - 1) / BLOCK, BLOCK>>>(a, n, inv_n, p);
	return cudaGetLastError() == cudaSuccess;
}

// fa <- IDFT( DFT(a) .* DFT(b) ) mod p, results left in workspace.fa
bool convolve_one_prime(NttWorkspace &ws, const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b, int n, int logn, u64 p) {
	if (!upload_twiddles(ws, ws.wf, n, p, false) ||
			!upload_twiddles(ws, ws.wi, n, p, true)) {
		return false;
	}
	if (!load_digits(ws, ws.fa, a, n) || !load_digits(ws, ws.fb, b, n)) {
		return false;
	}
	if (!forward_ntt(ws.fa, ws.wf, n, logn, p) ||
			!forward_ntt(ws.fb, ws.wf, n, logn, p)) {
		return false;
	}
	k_pointwise<<<(n + BLOCK - 1) / BLOCK, BLOCK>>>(ws.fa, ws.fb, n, p);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	return inverse_ntt(ws.fa, ws.wi, n, logn, p);
}

} // namespace

bool convolve_ntt_u16_to_limbs(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits) {
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

	thread_local NttWorkspace ws;
	if (!ws.ensure(n)) {
		return false;
	}

	// Prime 1 -> c1, prime 3 -> fa, then CRT into out.
	if (!convolve_one_prime(ws, a, b, n, logn, MOD1)) {
		return false;
	}
	if (cudaMemcpy(ws.c1, ws.fa, sizeof(u64) * static_cast<size_t>(n),
			cudaMemcpyDeviceToDevice) != cudaSuccess) {
		return false;
	}
	if (!convolve_one_prime(ws, a, b, n, logn, MOD3)) {
		return false;
	}
	k_crt<<<(static_cast<int>(result_size) + BLOCK - 1) / BLOCK, BLOCK>>>(
			ws.c1, ws.fa, ws.out, static_cast<int>(result_size));
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}

	ws.host_out.resize(result_size);
	if (cudaMemcpy(ws.host_out.data(), ws.out, sizeof(u64) * result_size,
			cudaMemcpyDeviceToHost) != cudaSuccess) {
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

}
