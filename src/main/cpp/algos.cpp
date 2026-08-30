#include "algos.h"
#include "cuda_convolution.h"
#include "cuda_runtime_state.h"
#include <cstdint>
#include <cstring>
#include <limits>
#include <new>
#include <utility>
#include <vector>

#ifdef BIGMATH_HAS_CUDA
#include "cuda_modular.h"
#include "cuda_ntt.h"
#endif

namespace bigmath {

#ifndef BIGMATH_NO_GMP

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

static void export_abs_mpz_to_u16_digits(
	mpz_ptr value,
	mp_bitcnt_t bits,
	std::vector<uint16_t> &out
);
static void write_u16_digits_to_mpz(
	mpz_ptr out,
	const std::vector<uint16_t> &digits,
	unsigned bits_per_digit
);
static cuda::DispatchDecision cuda_dispatch_decision(
	uint64_t left_bits,
	uint64_t right_bits,
	bool square
);

#ifdef BIGMATH_HAS_CUDA
static bool try_resident_modpow(mpz_ptr out, mpz_ptr base, mpz_ptr exp, mpz_ptr modulus) {
	const mp_bitcnt_t modulus_bits = mpz_sizeinbase(modulus, 2);
	const cuda::DispatchDecision dispatch = cuda_dispatch_decision(
		modulus_bits,
		modulus_bits,
		true
	);
	if (dispatch.backend != cuda::RuntimeBackend::NTT) return false;
	const size_t modulus_digits = static_cast<size_t>((modulus_bits + 15) / 16);
	if (modulus_digits == 0 ||
			modulus_digits > static_cast<size_t>((cuda::NTT_MAX_TRANSFORM_SIZE - 4) / 2)) {
		return false;
	}
	int transform_size = 1;
	const size_t required_transform = modulus_digits * 2 + 3;
	while (static_cast<size_t>(transform_size) < required_transform) transform_size <<= 1;
	const cuda::ModularOperationCosts costs =
		cuda::modular_operation_costs(transform_size);

	mpz_t reduced_base;
	mpz_t radix;
	mpz_t reduction_constant;
	mpz_t identity;
	mpz_init(reduced_base);
	mpz_init(radix);
	mpz_init(reduction_constant);
	mpz_init(identity);
	mpz_mod(reduced_base, base, modulus);
	mpz_setbit(radix, static_cast<mp_bitcnt_t>(modulus_digits * 16));

	cuda::ResidentReduction reduction = cuda::ResidentReduction::BARRETT;
	bool prepared = true;
	if (mpz_odd_p(modulus)) {
		reduction = cuda::ResidentReduction::MONTGOMERY;
		prepared = mpz_invert(reduction_constant, modulus, radix) != 0;
		if (prepared) {
			mpz_neg(reduction_constant, reduction_constant);
			mpz_mod(reduction_constant, reduction_constant, radix);
			mpz_mul(reduced_base, reduced_base, radix);
			mpz_mod(reduced_base, reduced_base, modulus);
			mpz_mod(identity, radix, modulus);
		}
	} else {
		mpz_set_ui(reduction_constant, 0);
		mpz_setbit(
			reduction_constant,
			static_cast<mp_bitcnt_t>(modulus_digits * 32)
		);
		mpz_tdiv_q(reduction_constant, reduction_constant, modulus);
		mpz_set_ui(identity, 1);
	}

	std::vector<uint16_t> base_digits;
	std::vector<uint16_t> exponent_digits;
	std::vector<uint16_t> modulus_value;
	std::vector<uint16_t> constant_digits;
	std::vector<uint16_t> identity_digits;
	std::vector<uint16_t> result_digits;
	bool completed = false;
	if (prepared) {
		export_abs_mpz_to_u16_digits(
			reduced_base,
			mpz_sizeinbase(reduced_base, 2),
			base_digits
		);
		export_abs_mpz_to_u16_digits(exp, mpz_sizeinbase(exp, 2), exponent_digits);
		export_abs_mpz_to_u16_digits(modulus, modulus_bits, modulus_value);
		export_abs_mpz_to_u16_digits(
			reduction_constant,
			mpz_sizeinbase(reduction_constant, 2),
			constant_digits
		);
		export_abs_mpz_to_u16_digits(
			identity,
			mpz_sizeinbase(identity, 2),
			identity_digits
		);
		completed = cuda::resident_modpow_u16(
			base_digits,
			exponent_digits,
			modulus_value,
			constant_digits,
			identity_digits,
			reduction,
			costs.multiply_nanos,
			costs.square_nanos,
			dispatch.max_queue_wait_nanos,
			result_digits
		);
	}
	if (completed) write_u16_digits_to_mpz(out, result_digits, 16);
	mpz_clear(reduced_base);
	mpz_clear(radix);
	mpz_clear(reduction_constant);
	mpz_clear(identity);
	return completed;
}
#endif

void modpow(mpz_ptr out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod) {
	static constexpr mp_bitcnt_t MODPOW_BIT_THRESHOLD = 524288;
	if (mpz_sgn(mod) <= 0 || mpz_sgn(exp) < 0 || mpz_cmp_ui(mod, 1) == 0 ||
			mpz_sizeinbase(mod, 2) < MODPOW_BIT_THRESHOLD) {
		mpz_powm(out, base, exp, mod);
		return;
	}
#ifdef BIGMATH_HAS_CUDA
	if (try_resident_modpow(out, base, exp, mod)) return;
	cuda::record_cpu_fallback();
#endif
	mpz_powm(out, base, exp, mod);
}

static void export_abs_mpz_to_u16_digits(mpz_ptr value, mp_bitcnt_t bits, std::vector<uint16_t> &out);
static void export_abs_mpz_to_digits(mpz_ptr value, mp_bitcnt_t bits, unsigned bits_per_digit, std::vector<uint16_t> &out);

struct CachedCudaU16Digits {
	mpz_ptr source = nullptr;
	size_t limb_count = 0;
	unsigned bits_per_digit = 0;
	std::vector<mp_limb_t> limbs;
	std::vector<uint16_t> digits;

	void clear() {
		source = nullptr;
		limb_count = 0;
		bits_per_digit = 0;
		limbs.clear();
		digits.clear();
	}

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

static void copy_abs_u64_limbs(mpz_ptr value, std::vector<uint64_t> &out) {
	const size_t count = abs_limb_count(value);
	out.resize(count);
	for (size_t i = 0; i < count; i++) {
		out[i] = static_cast<uint64_t>(value->_mp_d[i]);
	}
}

struct CudaMultiplyHostWorkspace {
	CachedCudaU16Digits ad;
	CachedCudaU16Digits bd;
	std::vector<uint16_t> conv;
	std::vector<uint64_t> packed_limbs;

	void clear() {
		ad.clear();
		bd.clear();
		conv.clear();
		packed_limbs.clear();
	}
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

static int cuda_transform_size(uint64_t left_bits, uint64_t right_bits) {
	const uint64_t digits = (left_bits + 15) / 16 + (right_bits + 15) / 16 - 1;
	if (digits > static_cast<uint64_t>(std::numeric_limits<int>::max() / 2)) return 0;
	int transform = 1;
	while (static_cast<uint64_t>(transform) < digits) transform <<= 1;
	return transform;
}

static cuda::DispatchDecision cuda_dispatch_decision(
		uint64_t left_bits,
		uint64_t right_bits,
		bool square
) {
	return cuda::choose_dispatch({
		left_bits,
		right_bits,
		cuda_transform_size(left_bits, right_bits),
		square,
		false
	});
}

static caching::ProductBackend product_backend(const cuda::DispatchDecision &decision) {
	if (decision.backend == cuda::RuntimeBackend::NTT) return caching::ProductBackend::CUDA_NTT;
	if (decision.backend == cuda::RuntimeBackend::CUFFT) return caching::ProductBackend::CUDA_CUFFT;
	return caching::ProductBackend::CPU_GMP;
}

bool cuda_dispatch_favorable(uint64_t left_bits, uint64_t right_bits, bool square) {
	return cuda_dispatch_decision(left_bits, right_bits, square).backend != cuda::RuntimeBackend::CPU;
}

static bool execute_cuda_multiply(
		mpz_ptr out,
		mpz_ptr abs_a,
		mpz_ptr abs_b,
		CudaMultiplyHostWorkspace &workspace,
		DirectCudaBackend backend,
		uint64_t max_queue_wait_nanos,
		bool cache_spectra
) {
#ifndef BIGMATH_HAS_CUDA
	(void)out;
	(void)abs_a;
	(void)abs_b;
	(void)workspace;
	(void)backend;
	(void)max_queue_wait_nanos;
	(void)cache_spectra;
	return false;
#else
	static constexpr unsigned CUDA_DIGIT_BITS[] = {16, 12, 8};
	const mp_bitcnt_t bits_a = mpz_sizeinbase(abs_a, 2);
	const mp_bitcnt_t bits_b = mpz_sizeinbase(abs_b, 2);
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
	if (backend == DirectCudaBackend::NTT) {
		const std::vector<uint16_t> &ad = workspace.ad.load(abs_a, bits_a, 16);
		const std::vector<uint16_t> &bd = abs_a == abs_b ? ad : workspace.bd.load(abs_b, bits_b, 16);
		if (cuda::convolve_ntt_u16_to_limbs(
				ad,
				bd,
				workspace.packed_limbs,
				16,
				GMP_NUMB_BITS,
				max_queue_wait_nanos
		)) {
			write_u64_limbs_to_mpz(out, workspace.packed_limbs);
			return true;
		}
		return false;
	}
#endif

	for (unsigned bits_per_digit : CUDA_DIGIT_BITS) {
		const std::vector<uint16_t> &ad = workspace.ad.load(abs_a, bits_a, bits_per_digit);
		const std::vector<uint16_t> &bd = abs_a == abs_b ? ad : workspace.bd.load(abs_b, bits_b, bits_per_digit);
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
		if (GMP_NUMB_BITS % bits_per_digit == 0) {
			if (!cuda::convolve_u16_digits_to_limbs(ad, bd, workspace.packed_limbs,
						bits_per_digit, GMP_NUMB_BITS, max_queue_wait_nanos, cache_spectra)) {
				continue;
			}
			write_u64_limbs_to_mpz(out, workspace.packed_limbs);
			return true;
		}
#else
		(void)workspace.packed_limbs;
#endif
		if (!cuda::convolve_u16_digits(
				ad,
				bd,
				workspace.conv,
				bits_per_digit,
				max_queue_wait_nanos,
				cache_spectra
		)) {
			continue;
		}
		write_u16_digits_to_mpz(out, workspace.conv, bits_per_digit);
		return true;
	}
	return false;
#endif
}

bool cuda_multiply_direct(
		mpz_ptr out,
		mpz_ptr a,
		mpz_ptr b,
		DirectCudaBackend backend,
		uint64_t max_queue_wait_nanos,
		bool cache_spectra,
		bool reset_host_cache
) {
	thread_local CudaMultiplyHostWorkspace workspace;
	if (reset_host_cache) workspace.clear();
	return execute_cuda_multiply(
		out,
		a,
		b,
		workspace,
		backend,
		max_queue_wait_nanos,
		cache_spectra
	);
}

static bool cuda_multiply(
		mpz_ptr out,
		mpz_ptr abs_a,
		mpz_ptr abs_b,
		const cuda::DispatchDecision &decision
) {
#ifndef BIGMATH_HAS_CUDA
	(void)out;
	(void)abs_a;
	(void)abs_b;
	(void)decision;
	return false;
#else
	if (decision.backend == cuda::RuntimeBackend::CPU) return false;
	thread_local CudaMultiplyHostWorkspace workspace;
	bool completed = false;
	if (decision.backend == cuda::RuntimeBackend::NTT) {
		completed = execute_cuda_multiply(
			out,
			abs_a,
			abs_b,
			workspace,
			DirectCudaBackend::NTT,
			decision.max_queue_wait_nanos,
			false
		);
	}
	if (!completed) {
		completed = execute_cuda_multiply(
			out,
			abs_a,
			abs_b,
			workspace,
			DirectCudaBackend::CUFFT,
			decision.max_queue_wait_nanos,
			true
		);
	}
	if (completed) cuda::record_multiply();
	else cuda::record_cpu_fallback();
	return completed;
#endif
}

static void store_product_result(
		const caching::ProductCacheKey *cache_key,
		bool admitted,
		mpz_ptr result
) {
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
	if (cache_key == nullptr || !admitted) return;
	if (!caching::product_result_fits(abs_limb_count(result))) {
		caching::record_product_cache_bypass();
		return;
	}
	try {
		std::vector<uint64_t> packed_limbs;
		copy_abs_u64_limbs(result, packed_limbs);
		caching::store_product(*cache_key, std::move(packed_limbs), true);
	} catch (const std::bad_alloc &) {
		// Product caching is optional; preserve the completed multiplication.
		caching::record_product_cache_bypass();
	}
#else
	(void)cache_key;
	(void)admitted;
	(void)result;
#endif
}

static bool fft_multiply_impl(
		mpz_ptr out,
		mpz_ptr a,
		mpz_ptr b,
		const caching::ProductCacheKey *cache_key,
		bool cuda_only
) {
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen == 0 || blen == 0) {
		mpz_set_ui(out, 0);
		return true;
	}

	bool a_neg = (mpz_sgn(a) < 0);
	bool b_neg = (mpz_sgn(b) < 0);
	const uint64_t bits_a = mpz_sizeinbase(a, 2);
	const uint64_t bits_b = mpz_sizeinbase(b, 2);
	const cuda::DispatchDecision dispatch = cuda_dispatch_decision(bits_a, bits_b, a == b);
	if (cuda_only && dispatch.backend == cuda::RuntimeBackend::CPU) return false;
	caching::ProductCacheKey resolved_cache_key{};
	const caching::ProductCacheKey *resolved_cache_key_ptr = nullptr;
	if (cache_key != nullptr) {
		resolved_cache_key = *cache_key;
		resolved_cache_key.config = caching::with_product_backend(
			cache_key->config,
			product_backend(dispatch)
		);
		resolved_cache_key_ptr = &resolved_cache_key;
	}
	bool admit_product = false;
#if GMP_NUMB_BITS % 16 == 0 && GMP_NUMB_BITS <= 64
	if (resolved_cache_key_ptr != nullptr) {
		caching::ProductCacheLookup lookup = caching::lookup_product(*resolved_cache_key_ptr);
		admit_product = lookup.admit;
		if (lookup.hit) {
			write_u64_limbs_to_mpz(out, *lookup.packed_limbs);
			if (a_neg != b_neg) mpz_neg(out, out);
			return true;
		}
	}
#endif

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

	if (cuda_multiply(out, abs_a, abs_b, dispatch)) {
		store_product_result(resolved_cache_key_ptr, admit_product, out);
		if (clear_abs_a) mpz_clear(abs_a_storage);
		if (clear_abs_b) mpz_clear(abs_b_storage);
		if (a_neg != b_neg) mpz_neg(out, out);
		return true;
	}

	if (cuda_only) {
		if (clear_abs_a) mpz_clear(abs_a_storage);
		if (clear_abs_b) mpz_clear(abs_b_storage);
		return false;
	}
	mpz_mul(out, a, b);
	store_product_result(resolved_cache_key_ptr, admit_product, out);
	if (clear_abs_a) mpz_clear(abs_a_storage);
	if (clear_abs_b) mpz_clear(abs_b_storage);
	return true;
}

void fft_multiply(
		mpz_ptr out,
		mpz_ptr a,
		mpz_ptr b,
		const caching::ProductCacheKey *cache_key
) {
	fft_multiply_impl(out, a, b, cache_key, false);
}

void fft_multiply_into(
		mpz_ptr out,
		mpz_ptr a,
		mpz_ptr b,
		const caching::ProductCacheKey *cache_key
) {
	if (out == a || out == b) {
		mpz_t tmp;
		mpz_init(tmp);
		fft_multiply_impl(tmp, a, b, cache_key, false);
		mpz_set(out, tmp);
		mpz_clear(tmp);
		return;
	}
	fft_multiply_impl(out, a, b, cache_key, false);
}

bool try_cuda_multiply(
		mpz_ptr out,
		mpz_ptr a,
		mpz_ptr b,
		const caching::ProductCacheKey *cache_key
) {
	if (out == a || out == b) {
		mpz_t temporary;
		mpz_init(temporary);
		const bool completed = fft_multiply_impl(temporary, a, b, cache_key, true);
		if (completed) mpz_set(out, temporary);
		mpz_clear(temporary);
		return completed;
	}
	return fft_multiply_impl(out, a, b, cache_key, true);
}

void accelerated_mul(
		mpz_ptr out,
		mpz_ptr a,
		mpz_ptr b,
		const caching::ProductCacheKey *cache_key
) {
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen + blen >= NTT_THRESHOLD) {
		fft_multiply_into(out, a, b, cache_key);
	} else {
		mpz_mul(out, a, b);
	}
}

#else
// Stubs when GMP not available
void fast_pow(mpz_ptr, mpz_ptr, uint64_t) {}
void fft_multiply(mpz_ptr, mpz_ptr, mpz_ptr, const caching::ProductCacheKey *) {}
void fft_multiply_into(mpz_ptr, mpz_ptr, mpz_ptr, const caching::ProductCacheKey *) {}
void accelerated_mul(mpz_ptr, mpz_ptr, mpz_ptr, const caching::ProductCacheKey *) {}
bool try_cuda_multiply(mpz_ptr, mpz_ptr, mpz_ptr, const caching::ProductCacheKey *) { return false; }
bool cuda_multiply_direct(
		mpz_ptr,
		mpz_ptr,
		mpz_ptr,
		DirectCudaBackend,
		uint64_t,
		bool,
		bool
) { return false; }
void modpow(mpz_ptr, mpz_ptr, mpz_ptr, mpz_ptr) {}
#endif // BIGMATH_NO_GMP

}
