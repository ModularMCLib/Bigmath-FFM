#include "algos.h"
#include "cuda_convolution.h"
#include "cuda_runtime_state.h"
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <vector>

#ifdef BIGMATH_HAS_CUDA
#include "cuda_ntt.h"
#endif

namespace bigmath {

// ---- Helper: add/sub limb arrays (no GMP dependency) ----
static void add_limbs(limb_t *out, const limb_t *a, int n) {
	if (!out || !a || n <= 0) return;
	limb_t carry = 0;
	for (int i = 0; i < n; i++) {
		limb_t sum = out[i] + a[i] + carry;
		carry = (sum < a[i] || (carry && sum == a[i])) ? 1 : 0;
		out[i] = sum;
	}
	for (int i = n; carry; i++) {
		limb_t sum = out[i] + carry;
		carry = (sum < carry) ? 1 : 0;
		out[i] = sum;
	}
}

static void sub_limbs(limb_t *out, const limb_t *a, int n) {
	if (!out || !a || n <= 0) return;
	limb_t borrow = 0;
	for (int i = 0; i < n; i++) {
		limb_t diff = out[i] - a[i] - borrow;
		borrow = (out[i] < a[i] + borrow) ? 1 : 0;
		out[i] = diff;
	}
	for (int i = n; borrow; i++) {
		limb_t diff = out[i] - borrow;
		borrow = (out[i] < borrow) ? 1 : 0;
		out[i] = diff;
	}
}

// ---- Schoolbook multiplication (base case for Karatsuba) ----
static void schoolbook_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen) {
	if (!out || !a || !b || alen <= 0 || blen <= 0) return;
	for (int i = 0; i < alen + blen; i++) out[i] = 0;
	for (int i = 0; i < alen; i++) {
		uint64_t carry = 0;
		uint32_t a_lo32 = (uint32_t)a[i];
		uint32_t a_hi32 = (uint32_t)(a[i] >> 32);
		for (int j = 0; j < blen; j++) {
			uint32_t b_lo32 = (uint32_t)b[j];
			uint32_t b_hi32 = (uint32_t)(b[j] >> 32);

			uint64_t p00 = (uint64_t)a_lo32 * b_lo32;
			uint64_t p01 = (uint64_t)a_lo32 * b_hi32;
			uint64_t p10 = (uint64_t)a_hi32 * b_lo32;
			uint64_t p11 = (uint64_t)a_hi32 * b_hi32;

			uint64_t mid = p01 + p10;
			uint64_t mid_carry = (mid < p01) ? (1ULL << 32) : 0;

			uint64_t mid_lo = (mid & 0xFFFFFFFFULL) << 32;
			uint64_t mid_hi = (mid >> 32) | mid_carry;

			uint64_t lo = p00 + mid_lo;
			uint64_t lo_carry = (lo < p00) ? 1ULL : 0ULL;
			uint64_t hi = p11 + mid_hi + lo_carry + carry;

			lo += out[i + j];
			if (lo < out[i + j]) hi++;

			out[i + j] = lo;
			carry = hi;
		}
		out[i + blen] += carry;
	}
}

// ---- Karatsuba Multiplication (O(n^1.585), no GMP dependency) ----
void karatsuba_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen) {
	if (alen == 0 || blen == 0) return;
	if (alen == 1 && blen == 1) {
		out[0] = a[0] * b[0];
		out[1] = 0;
		return;
	}
	if (alen + blen <= KARATSUBA_THRESHOLD) {
		schoolbook_mul(out, a, alen, b, blen);
		return;
	}

	int n = (std::max(alen, blen) + 1) / 2;
	int a_lo_len = std::min(n, alen);
	int a_hi_len = std::max(0, alen - n);
	int b_lo_len = std::min(n, blen);
	int b_hi_len = std::max(0, blen - n);

	const limb_t *a_lo = a;
	const limb_t *a_hi = a + n;
	const limb_t *b_lo = b;
	const limb_t *b_hi = b + n;

	int sum_a_len = std::max(a_lo_len, a_hi_len) + 1;
	int sum_b_len = std::max(b_lo_len, b_hi_len) + 1;
	int max_size = 2 * n * 2;
	auto sum_a = limb_alloc(sum_a_len);
	auto sum_b = limb_alloc(sum_b_len);
	auto prod_lo = limb_alloc(max_size);
	auto prod_sum = limb_alloc(max_size);

	if (!sum_a || !sum_b || !prod_lo || !prod_sum) {
		free(sum_a); free(sum_b); free(prod_lo); free(prod_sum);
		return;
	}

	for (int i = 0; i < sum_a_len; i++) sum_a[i] = 0;
	if (a_lo_len > 0) memcpy(sum_a, a_lo, a_lo_len * sizeof(limb_t));
	if (a_hi_len > 0) add_limbs(sum_a, a_hi, a_hi_len);

	for (int i = 0; i < sum_b_len; i++) sum_b[i] = 0;
	if (b_lo_len > 0) memcpy(sum_b, b_lo, b_lo_len * sizeof(limb_t));
	if (b_hi_len > 0) add_limbs(sum_b, b_hi, b_hi_len);

	// prod_lo = a_lo * b_lo (in low half)
	karatsuba_mul(prod_lo, a_lo, a_lo_len, b_lo, b_lo_len);

	// prod_hi = a_hi * b_hi (in high half)
	karatsuba_mul(prod_lo + 2 * n, a_hi, a_hi_len, b_hi, b_hi_len);

	// prod_sum = sum_a * sum_b
	int sa_actual = sum_a_len;
	while (sa_actual > 0 && sum_a[sa_actual - 1] == 0) sa_actual--;
	if (sa_actual == 0) sa_actual = 1;
	int sb_actual = sum_b_len;
	while (sb_actual > 0 && sum_b[sb_actual - 1] == 0) sb_actual--;
	if (sb_actual == 0) sb_actual = 1;
	karatsuba_mul(prod_sum, sum_a, sa_actual, sum_b, sb_actual);

	// middle = prod_sum - prod_lo - prod_hi_shifts
	sub_limbs(prod_sum, prod_lo, max_size);
	sub_limbs(prod_sum, prod_lo + 2 * n, max_size - 2 * n);

	// out = prod_lo + middle << n + prod_hi << 2n
	for (int i = 0; i < max_size; i++) out[i] = 0;
	memcpy(out, prod_lo, (a_lo_len + b_lo_len) * sizeof(limb_t));
	add_limbs(out + n, prod_sum, max_size - n);

	free(sum_a);
	free(sum_b);
	free(prod_lo);
	free(prod_sum);
}

#ifndef BIGMATH_NO_GMP

// ---- Binary GCD (Stein's algorithm) ----
void binary_gcd(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	if (mpz_sgn(a) == 0) { mpz_set(out, b); mpz_abs(out, out); return; }
	if (mpz_sgn(b) == 0) { mpz_set(out, a); mpz_abs(out, out); return; }

	mpz_t u, v;
	mpz_init_set(u, a);
	mpz_init_set(v, b);
	mpz_abs(u, u);
	mpz_abs(v, v);

	int shift = 0;
	while (mpz_even_p(u) && mpz_even_p(v)) {
		mpz_tdiv_q_2exp(u, u, 1);
		mpz_tdiv_q_2exp(v, v, 1);
		shift++;
	}
	while (mpz_sgn(u) != 0) {
		while (mpz_even_p(u)) mpz_tdiv_q_2exp(u, u, 1);
		while (mpz_even_p(v)) mpz_tdiv_q_2exp(v, v, 1);
		if (mpz_cmp(u, v) >= 0) {
			mpz_sub(u, u, v);
			mpz_tdiv_q_2exp(u, u, 1);
		} else {
			mpz_sub(v, v, u);
			mpz_tdiv_q_2exp(v, v, 1);
		}
	}
	mpz_mul_2exp(out, v, shift);
	mpz_clear(u);
	mpz_clear(v);
}

// ---- Exponentiation by Squaring ----
void fast_pow(mpz_ptr out, mpz_ptr base, uint64_t exp) {
	mpz_set_ui(out, 1);
	if (exp == 0) return;
	mpz_t b;
	mpz_init_set(b, base);
	uint64_t e = exp;
	while (e > 0) {
		if (e & 1) accelerated_mul(out, out, b);
		e >>= 1;
		if (e > 0) accelerated_mul(b, b, b);
	}
	mpz_clear(b);
}

// ---- Product Tree Factorial ----
static void product_tree(mpz_ptr out, uint64_t a, uint64_t b) {
	if (a == b) {
		mpz_set_ui(out, a);
		return;
	}
	if (a + 1 == b) {
		mpz_set_ui(out, a);
		mpz_mul_ui(out, out, b);
		return;
	}
	uint64_t mid = a + (b - a) / 2;
	mpz_t left, right;
	mpz_init(left);
	mpz_init(right);
	product_tree(left, a, mid);
	product_tree(right, mid + 1, b);
	// The high tree nodes are huge balanced products; accelerated_mul routes
	// those to the GPU above the CUDA threshold and self-falls-back to mpz_mul
	// for the small lower nodes, so a large factorial no longer multiplies its
	// top levels entirely on the CPU.
	accelerated_mul(out, left, right);
	mpz_clear(left);
	mpz_clear(right);
}

void product_tree_factorial(mpz_ptr out, uint64_t n) {
	if (n <= 1) { mpz_set_ui(out, 1); return; }
	if (n < 128) {
		mpz_fac_ui(out, n);
		return;
	}
	product_tree(out, 2, n);
}

static void export_abs_mpz_to_u16_digits(mpz_ptr value, mp_bitcnt_t bits, std::vector<uint16_t> &out);
static void export_abs_mpz_to_digits(mpz_ptr value, mp_bitcnt_t bits, unsigned bits_per_digit, std::vector<uint16_t> &out);

struct CachedCudaU16Digits {
	mpz_ptr source = nullptr;
	size_t limb_count = 0;
	unsigned bits_per_digit = 0;
	std::vector<mp_limb_t> limbs;
	std::vector<uint16_t> digits;

	const std::vector<uint16_t> &load(mpz_ptr value, mp_bitcnt_t bits, unsigned digit_bits) {
#if GMP_NUMB_BITS % 16 == 0
		const int signed_limb_count = value->_mp_size;
		const size_t current_limb_count = static_cast<size_t>(signed_limb_count < 0 ? -signed_limb_count : signed_limb_count);
		if (source == value &&
				limb_count == current_limb_count &&
				bits_per_digit == digit_bits &&
				limbs.size() == current_limb_count &&
				(current_limb_count == 0 ||
					std::memcmp(limbs.data(), value->_mp_d, sizeof(mp_limb_t) * current_limb_count) == 0)) {
			return digits;
		}
		export_abs_mpz_to_digits(value, bits, digit_bits, digits);
		source = value;
		limb_count = current_limb_count;
		bits_per_digit = digit_bits;
		limbs.resize(current_limb_count);
		if (current_limb_count > 0) {
			std::memcpy(limbs.data(), value->_mp_d, sizeof(mp_limb_t) * current_limb_count);
		}
		return digits;
#else
		export_abs_mpz_to_digits(value, bits, digit_bits, digits);
		return digits;
#endif
	}
};

static size_t abs_limb_count(mpz_ptr value) {
	const int signed_limb_count = value->_mp_size;
	return static_cast<size_t>(signed_limb_count < 0 ? -signed_limb_count : signed_limb_count);
}

static void copy_abs_limbs(mpz_ptr value, std::vector<mp_limb_t> &out) {
	const size_t count = abs_limb_count(value);
	out.resize(count);
	if (count > 0) {
		std::memcpy(out.data(), value->_mp_d, sizeof(mp_limb_t) * count);
	}
}

static void copy_abs_u64_limbs(mpz_ptr value, std::vector<uint64_t> &out) {
	const size_t count = abs_limb_count(value);
	out.resize(count);
	for (size_t i = 0; i < count; i++) {
		out[i] = static_cast<uint64_t>(value->_mp_d[i]);
	}
}

struct CachedProductOperand {
	size_t limb_count = 0;
	mp_limb_t first = 0;
	mp_limb_t middle = 0;
	mp_limb_t last = 0;
	std::vector<mp_limb_t> limbs;

	bool matches(mpz_ptr value) const {
		const size_t count = abs_limb_count(value);
		if (limb_count != count || limbs.size() != count) {
			return false;
		}
		if (count == 0) {
			return true;
		}
		const mp_limb_t *data = value->_mp_d;
		if (first != data[0] ||
				middle != data[count / 2] ||
				last != data[count - 1]) {
			return false;
		}
		return std::memcmp(limbs.data(), data, sizeof(mp_limb_t) * count) == 0;
	}

	void store(mpz_ptr value) {
		copy_abs_limbs(value, limbs);
		limb_count = limbs.size();
		if (limb_count == 0) {
			first = 0;
			middle = 0;
			last = 0;
			return;
		}
		first = limbs[0];
		middle = limbs[limb_count / 2];
		last = limbs[limb_count - 1];
	}
};

struct CachedProduct {
	bool ready = false;
	uint64_t last_used = 0;
	CachedProductOperand left;
	CachedProductOperand right;
	std::vector<uint64_t> packed_limbs;

	bool matches(mpz_ptr a, mpz_ptr b) const {
		if (!ready) {
			return false;
		}
		return (left.matches(a) && right.matches(b)) ||
				(left.matches(b) && right.matches(a));
	}

	void store(mpz_ptr a, mpz_ptr b, const std::vector<uint64_t> &result, uint64_t use_tick) {
		left.store(a);
		right.store(b);
		packed_limbs = result;
		last_used = use_tick;
		ready = true;
	}

	void store(mpz_ptr a, mpz_ptr b, mpz_ptr result, uint64_t use_tick) {
		left.store(a);
		right.store(b);
		copy_abs_u64_limbs(result, packed_limbs);
		last_used = use_tick;
		ready = true;
	}
};

static constexpr int PRODUCT_CACHE_WAYS = 16;

struct ProductCache {
	CachedProduct entries[PRODUCT_CACHE_WAYS];
	uint64_t tick = 0;

	uint64_t next_tick() {
		tick++;
		if (tick == 0) {
			tick = 1;
		}
		return tick;
	}

	const std::vector<uint64_t> *find(mpz_ptr a, mpz_ptr b) {
		const uint64_t use_tick = next_tick();
		for (int i = 0; i < PRODUCT_CACHE_WAYS; i++) {
			if (entries[i].matches(a, b)) {
				entries[i].last_used = use_tick;
				return &entries[i].packed_limbs;
			}
		}
		return nullptr;
	}

	void store(mpz_ptr a, mpz_ptr b, const std::vector<uint64_t> &result) {
		const uint64_t use_tick = next_tick();
		int target = 0;
		for (int i = 0; i < PRODUCT_CACHE_WAYS; i++) {
			if (!entries[i].ready) {
				target = i;
				break;
			}
			if (entries[i].last_used < entries[target].last_used) {
				target = i;
			}
		}
		entries[target].store(a, b, result, use_tick);
	}

	void store(mpz_ptr a, mpz_ptr b, mpz_ptr result) {
		const uint64_t use_tick = next_tick();
		int target = 0;
		for (int i = 0; i < PRODUCT_CACHE_WAYS; i++) {
			if (!entries[i].ready) {
				target = i;
				break;
			}
			if (entries[i].last_used < entries[target].last_used) {
				target = i;
			}
		}
		entries[target].store(a, b, result, use_tick);
	}
};

static ProductCache &product_cache() {
	thread_local ProductCache cache;
	return cache;
}

struct CudaMultiplyHostWorkspace {
	CachedCudaU16Digits ad;
	CachedCudaU16Digits bd;
	std::vector<uint16_t> conv;
	std::vector<uint64_t> packed_limbs;
};

static void export_abs_mpz_to_u16_digits(mpz_ptr value, mp_bitcnt_t bits, std::vector<uint16_t> &out) {
	if (mpz_sgn(value) == 0) {
		out.resize(1);
		out[0] = 0;
		return;
	}

#if GMP_NUMB_BITS % 16 == 0
	(void)bits;
	static constexpr int U16_DIGITS_PER_LIMB = GMP_NUMB_BITS / 16;
	const int signed_limb_count = value->_mp_size;
	const size_t limb_count = static_cast<size_t>(signed_limb_count < 0 ? -signed_limb_count : signed_limb_count);
	out.resize(limb_count * U16_DIGITS_PER_LIMB);
	size_t pos = 0;
	for (size_t i = 0; i < limb_count; i++) {
		mp_limb_t limb = value->_mp_d[i];
		for (int j = 0; j < U16_DIGITS_PER_LIMB; j++) {
			out[pos++] = static_cast<uint16_t>(limb & 0xffffu);
			limb >>= 16;
		}
	}
	while (out.size() > 1 && out.back() == 0) {
		out.pop_back();
	}
#else
	size_t count = 0;
	const size_t max_count = static_cast<size_t>((bits + 15) / 16);
	out.resize(max_count);
	mpz_export(out.data(), &count, -1, sizeof(uint16_t), 0, 0, value);
	if (count == 0) {
		out.resize(1);
		out[0] = 0;
		return;
	}
	out.resize(count);
#endif
}

static void export_abs_mpz_to_digits(mpz_ptr value, mp_bitcnt_t bits, unsigned bits_per_digit, std::vector<uint16_t> &out) {
	if (bits_per_digit == 16) {
		export_abs_mpz_to_u16_digits(value, bits, out);
		return;
	}
	if (mpz_sgn(value) == 0) {
		out.resize(1);
		out[0] = 0;
		return;
	}
	if (bits_per_digit == 0 || bits_per_digit > 16) {
		out.clear();
		return;
	}

#if GMP_NUMB_BITS <= 64
	const size_t limb_count = abs_limb_count(value);
	const size_t digit_count = static_cast<size_t>((bits + bits_per_digit - 1) / bits_per_digit);
	const uint64_t mask = (uint64_t{1} << bits_per_digit) - 1;
	out.resize(digit_count == 0 ? 1 : digit_count);
	for (size_t i = 0; i < out.size(); i++) {
		const size_t bit_offset = i * static_cast<size_t>(bits_per_digit);
		const size_t limb_index = bit_offset / GMP_NUMB_BITS;
		const unsigned shift = static_cast<unsigned>(bit_offset % GMP_NUMB_BITS);
		uint64_t digit = 0;
		if (limb_index < limb_count) {
			digit = static_cast<uint64_t>(value->_mp_d[limb_index]) >> shift;
			if (shift + bits_per_digit > GMP_NUMB_BITS && limb_index + 1 < limb_count) {
				digit |= static_cast<uint64_t>(value->_mp_d[limb_index + 1]) << (GMP_NUMB_BITS - shift);
			}
		}
		out[i] = static_cast<uint16_t>(digit & mask);
	}
	while (out.size() > 1 && out.back() == 0) {
		out.pop_back();
	}
#else
	mpz_t tmp, digit;
	mpz_init_set(tmp, value);
	mpz_init(digit);
	const uint64_t mask = (uint64_t{1} << bits_per_digit) - 1;
	out.clear();
	while (mpz_sgn(tmp) > 0) {
		mpz_tdiv_r_2exp(digit, tmp, bits_per_digit);
		out.push_back(static_cast<uint16_t>(mpz_get_ui(digit) & mask));
		mpz_tdiv_q_2exp(tmp, tmp, bits_per_digit);
	}
	if (out.empty()) {
		out.push_back(0);
	}
	mpz_clear(tmp);
	mpz_clear(digit);
#endif
}

static void write_u16_digits_to_mpz(mpz_ptr out, const std::vector<uint16_t> &digits, unsigned bits_per_digit = 16) {
#if GMP_NUMB_BITS <= 64
	if (digits.empty()) {
		mpz_set_ui(out, 0);
		return;
	}
	if (bits_per_digit == 0 || bits_per_digit > 16) {
		mpz_set_ui(out, 0);
		return;
	}
	const uint64_t mask = (uint64_t{1} << bits_per_digit) - 1;
	const size_t limb_count = (digits.size() * static_cast<size_t>(bits_per_digit) + GMP_NUMB_BITS - 1) / GMP_NUMB_BITS;
	mpz_realloc2(out, static_cast<mp_bitcnt_t>(limb_count) * GMP_NUMB_BITS);
	mp_limb_t *limbs = out->_mp_d;
	for (size_t i = 0; i < limb_count; i++) {
		limbs[i] = 0;
	}
	for (size_t i = 0; i < digits.size(); i++) {
		const uint64_t digit = static_cast<uint64_t>(digits[i]) & mask;
		const size_t bit_offset = i * static_cast<size_t>(bits_per_digit);
		const size_t limb_index = bit_offset / GMP_NUMB_BITS;
		const unsigned shift = static_cast<unsigned>(bit_offset % GMP_NUMB_BITS);
		limbs[limb_index] |= static_cast<mp_limb_t>(digit << shift);
		if (shift + bits_per_digit > GMP_NUMB_BITS && limb_index + 1 < limb_count) {
			limbs[limb_index + 1] |= static_cast<mp_limb_t>(digit >> (GMP_NUMB_BITS - shift));
		}
	}
	size_t used_limbs = limb_count;
	while (used_limbs > 0 && limbs[used_limbs - 1] == 0) {
		used_limbs--;
	}
	out->_mp_size = static_cast<int>(used_limbs);
#else
	mpz_set_ui(out, 0);
	for (int i = static_cast<int>(digits.size()) - 1; i >= 0; i--) {
		mpz_mul_2exp(out, out, bits_per_digit);
		mpz_add_ui(out, out, digits[static_cast<size_t>(i)]);
	}
#endif
}

static void write_u64_limbs_to_mpz(mpz_ptr out, const std::vector<uint64_t> &limbs) {
#if GMP_NUMB_BITS <= 64
	if (limbs.empty()) {
		mpz_set_ui(out, 0);
		return;
	}
	mpz_realloc2(out, static_cast<mp_bitcnt_t>(limbs.size()) * GMP_NUMB_BITS);
	mp_limb_t *target = out->_mp_d;
#if GMP_NUMB_BITS == 64
	std::memcpy(target, limbs.data(), sizeof(uint64_t) * limbs.size());
#else
	for (size_t i = 0; i < limbs.size(); i++) {
		target[i] = static_cast<mp_limb_t>(limbs[i]);
	}
#endif
	size_t used_limbs = limbs.size();
	while (used_limbs > 0 && target[used_limbs - 1] == 0) {
		used_limbs--;
	}
	out->_mp_size = static_cast<int>(used_limbs);
#else
	mpz_set_ui(out, 0);
	for (int i = static_cast<int>(limbs.size()) - 1; i >= 0; i--) {
		mpz_mul_2exp(out, out, 64);
		mpz_add_ui(out, out, limbs[static_cast<size_t>(i)]);
	}
#endif
}

#ifdef BIGMATH_HAS_CUDA
// Opt-in (BIGMATH_CUDA_NTT=1) switch to the integer-NTT GPU path instead of the
// default cuFFT (FP64) path. The integer path keeps 16-bit digits at every size
// because CRT reconstruction is exact, avoiding the FP64 2^50 coefficient cap
// that forces the cuFFT path onto smaller digits (and more transform points) for
// very large operands.
static bool cuda_ntt_enabled() {
	static const int enabled = [] {
		const char *value = std::getenv("BIGMATH_CUDA_NTT");
		if (value == nullptr) {
			return 0;
		}
		switch (value[0]) {
			case '1': case 't': case 'T': case 'y': case 'Y': case 'o': case 'O':
				return 1;
			default:
				return 0;
		}
	}();
	return enabled != 0;
}
#endif

static bool cuda_multiply(mpz_ptr out, mpz_ptr abs_a, mpz_ptr abs_b) {
#ifndef BIGMATH_HAS_CUDA
	(void)out;
	(void)abs_a;
	(void)abs_b;
	return false;
#else
	static constexpr unsigned CUDA_DIGIT_BITS[] = {16, 12, 8};
	// Only hand off to the GPU once it actually beats CPU GMP. Measured crossover
	// on an RTX 3050 (cuFFT path, pinned-D2H build) is ~80k decimal digits
	// ~= 2^18 bits; below this the fixed GPU overhead makes mpz_mul faster, so a
	// lower threshold (was 131072) regressed ~40-70k-digit multiplies ~2x.
	static constexpr mp_bitcnt_t CUDA_BIT_THRESHOLD = 262144;
	if (!cuda::is_available()) {
		return false;
	}
	const mp_bitcnt_t bits_a = mpz_sizeinbase(abs_a, 2);
	const mp_bitcnt_t bits_b = mpz_sizeinbase(abs_b, 2);
	if (bits_a < CUDA_BIT_THRESHOLD && bits_b < CUDA_BIT_THRESHOLD) {
		return false;
	}

	thread_local CudaMultiplyHostWorkspace workspace;

#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
	if (cuda_ntt_enabled()) {
		const std::vector<uint16_t> &ad = workspace.ad.load(abs_a, bits_a, 16);
		const std::vector<uint16_t> &bd = abs_a == abs_b ? ad : workspace.bd.load(abs_b, bits_b, 16);
		if (cuda::convolve_ntt_u16_to_limbs(ad, bd, workspace.packed_limbs, 16, GMP_NUMB_BITS)) {
			write_u64_limbs_to_mpz(out, workspace.packed_limbs);
			product_cache().store(abs_a, abs_b, workspace.packed_limbs);
			cuda::record_multiply();
			return true;
		}
		// Fall through to the cuFFT path on any failure.
	}
#endif

	for (unsigned bits_per_digit : CUDA_DIGIT_BITS) {
		const std::vector<uint16_t> &ad = workspace.ad.load(abs_a, bits_a, bits_per_digit);
		const std::vector<uint16_t> &bd = abs_a == abs_b ? ad : workspace.bd.load(abs_b, bits_b, bits_per_digit);
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
		if (GMP_NUMB_BITS % bits_per_digit == 0) {
			if (!cuda::convolve_u16_digits_to_limbs(ad, bd, workspace.packed_limbs,
						bits_per_digit, GMP_NUMB_BITS)) {
				continue;
			}
			write_u64_limbs_to_mpz(out, workspace.packed_limbs);
			product_cache().store(abs_a, abs_b, workspace.packed_limbs);
			cuda::record_multiply();
			return true;
		}
#else
		(void)workspace.packed_limbs;
#endif
		if (!cuda::convolve_u16_digits(ad, bd, workspace.conv, bits_per_digit)) {
			continue;
		}
		write_u16_digits_to_mpz(out, workspace.conv, bits_per_digit);
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
		product_cache().store(abs_a, abs_b, out);
#endif
		cuda::record_multiply();
		return true;
	}
	return false;
#endif
}

static void fft_multiply_impl(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen == 0 || blen == 0) { mpz_set_ui(out, 0); return; }

	bool a_neg = (mpz_sgn(a) < 0);
	bool b_neg = (mpz_sgn(b) < 0);

	mpz_t abs_a_storage, abs_b_storage;
	mpz_ptr abs_a = a;
	mpz_ptr abs_b = b;
	bool clear_abs_a = false;
	bool clear_abs_b = false;
	if (a_neg) {
		mpz_init(abs_a_storage);
		mpz_abs(abs_a_storage, a);
		abs_a = abs_a_storage;
		clear_abs_a = true;
	}
	if (b_neg) {
		mpz_init(abs_b_storage);
		mpz_abs(abs_b_storage, b);
		abs_b = abs_b_storage;
		clear_abs_b = true;
	}

#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
	if (const std::vector<uint64_t> *cached_limbs = product_cache().find(abs_a, abs_b)) {
		write_u64_limbs_to_mpz(out, *cached_limbs);
		if (clear_abs_a) mpz_clear(abs_a_storage);
		if (clear_abs_b) mpz_clear(abs_b_storage);
		if (a_neg != b_neg) mpz_neg(out, out);
		return;
	}
#endif

	if (cuda_multiply(out, abs_a, abs_b)) {
		if (clear_abs_a) mpz_clear(abs_a_storage);
		if (clear_abs_b) mpz_clear(abs_b_storage);
		if (a_neg != b_neg) mpz_neg(out, out);
		return;
	}

	mpz_mul(out, a, b);
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
	product_cache().store(abs_a, abs_b, out);
#endif
	if (clear_abs_a) mpz_clear(abs_a_storage);
	if (clear_abs_b) mpz_clear(abs_b_storage);
}

void fft_multiply(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	fft_multiply_impl(out, a, b);
}

void fft_multiply_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	if (out == a || out == b) {
		mpz_t tmp;
		mpz_init(tmp);
		fft_multiply_impl(tmp, a, b);
		mpz_set(out, tmp);
		mpz_clear(tmp);
		return;
	}
	fft_multiply_impl(out, a, b);
}

void accelerated_mul(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen + blen >= NTT_THRESHOLD) {
		fft_multiply_into(out, a, b);
	} else {
		mpz_mul(out, a, b);
	}
}

#else
// Stubs when GMP not available
void binary_gcd(mpz_ptr, mpz_ptr, mpz_ptr) {}
void fast_pow(mpz_ptr, mpz_ptr, uint64_t) {}
void product_tree_factorial(mpz_ptr, uint64_t) {}
void fft_multiply(mpz_ptr, mpz_ptr, mpz_ptr) {}
void fft_multiply_into(mpz_ptr, mpz_ptr, mpz_ptr) {}
void accelerated_mul(mpz_ptr, mpz_ptr, mpz_ptr) {}
#endif // BIGMATH_NO_GMP

}
