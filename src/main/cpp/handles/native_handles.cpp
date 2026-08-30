#include "../bigmath_ffm.h"
#include "native_backend.h"

#include <atomic>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <new>
#include <vector>

#ifndef BIGMATH_BUILD_ID
#define BIGMATH_BUILD_ID "development"
#endif

namespace {

std::atomic<uint64_t> next_handle_id{1};

uint64_t allocate_handle_id() {
	return next_handle_id.fetch_add(1, std::memory_order_relaxed);
}

BigIntHandle *wrap_bigint(mpz_ptr value, bool read_only = false) {
	if (value == nullptr) {
		return nullptr;
	}
	auto *handle = new (std::nothrow) BigIntHandle{
		allocate_handle_id(),
		0,
		read_only,
		value
	};
	if (handle == nullptr) {
		bigint_backend_free(value);
	}
	return handle;
}

BigDeciHandle *wrap_bigdecimal(mpfr_ptr value, bool read_only = false) {
	if (value == nullptr) {
		return nullptr;
	}
	auto *handle = new (std::nothrow) BigDeciHandle{
		allocate_handle_id(),
		0,
		read_only,
		value
	};
	if (handle == nullptr) {
		bigdecimal_backend_free(value);
	}
	return handle;
}

bool mutable_bigint(BigIntHandle *handle) {
	return handle != nullptr && handle->value != nullptr && !handle->read_only;
}

bool mutable_bigdecimal(BigDeciHandle *handle) {
	return handle != nullptr && handle->value != nullptr && !handle->read_only;
}

template<typename Handle>
ProductCacheKey product_cache_key(Handle *left, Handle *right, uint64_t config) {
	return ProductCacheKey::canonical(
		{left->id, left->version},
		{right->id, right->version},
		config
	);
}

#ifdef BIGMATH_HAS_GMP
std::vector<uint8_t> export_twos_complement(mpz_ptr value) {
	if (mpz_sgn(value) == 0) {
		return {0};
	}

	mpz_t encoded;
	mpz_init(encoded);
	size_t byte_count;
	if (mpz_sgn(value) > 0) {
		const size_t magnitude_bits = mpz_sizeinbase(value, 2);
		byte_count = (magnitude_bits + 1 + 7) / 8;
		mpz_set(encoded, value);
	} else {
		mpz_t adjusted;
		mpz_init(adjusted);
		mpz_abs(adjusted, value);
		mpz_sub_ui(adjusted, adjusted, 1);
		const size_t signed_bits = mpz_sgn(adjusted) == 0 ? 1 : mpz_sizeinbase(adjusted, 2) + 1;
		byte_count = (signed_bits + 7) / 8;
		mpz_set_ui(encoded, 1);
		mpz_mul_2exp(encoded, encoded, static_cast<mp_bitcnt_t>(byte_count * 8));
		mpz_add(encoded, encoded, value);
		mpz_clear(adjusted);
	}

	std::vector<uint8_t> exported(byte_count, 0);
	size_t written = 0;
	mpz_export(exported.data(), &written, 1, 1, 1, 0, encoded);
	std::vector<uint8_t> result(byte_count, 0);
	if (written > 0) {
		std::memcpy(result.data() + byte_count - written, exported.data(), written);
	}
	mpz_clear(encoded);
	return result;
}
#endif

}

uint32_t bigmath_abi_version() {
	return 2;
}

const char *bigmath_build_id() {
	return BIGMATH_BUILD_ID;
}

uint64_t bigmath_capabilities() {
	uint64_t capabilities = UINT64_C(1);
#ifdef BIGMATH_HAS_GMP
	capabilities |= UINT64_C(1) << 1;
	capabilities |= UINT64_C(1) << 2;
#endif
#ifdef BIGMATH_HAS_CUDA
	capabilities |= UINT64_C(1) << 3;
#endif
	return capabilities;
}

uint64_t bigmath_product_cache_hits() {
	return bigmath::caching::product_cache_metrics().hits;
}

uint64_t bigmath_product_cache_misses() {
	return bigmath::caching::product_cache_metrics().misses;
}

uint64_t bigmath_product_cache_admissions() {
	return bigmath::caching::product_cache_metrics().admissions;
}

uint64_t bigmath_product_cache_evictions() {
	return bigmath::caching::product_cache_metrics().evictions;
}

uint64_t bigmath_product_cache_bytes() {
	return bigmath::caching::product_cache_metrics().bytes;
}

BigIntHandle *bigint_from_long(int64_t val) {
	mpz_ptr value = nullptr;
	bigint_backend_from_long(&value, val);
	return wrap_bigint(value);
}

BigIntHandle *bigint_from_string(const char *str, int radix) {
	mpz_ptr value = nullptr;
	bigint_backend_from_string(&value, str, radix);
	return wrap_bigint(value);
}

BigIntHandle *bigint_from_twos_complement(const uint8_t *bytes, uint64_t size) {
#ifndef BIGMATH_HAS_GMP
	(void)bytes;
	(void)size;
	return nullptr;
#else
	if (bytes == nullptr || size == 0 || size > static_cast<uint64_t>(SIZE_MAX)) {
		return nullptr;
	}
	mpz_ptr value = nullptr;
	bigint_backend_init(&value);
	if (value == nullptr) {
		return nullptr;
	}
	mpz_import(value, static_cast<size_t>(size), 1, 1, 1, 0, bytes);
	if ((bytes[0] & 0x80u) != 0) {
		mpz_t modulus;
		mpz_init_set_ui(modulus, 1);
		mpz_mul_2exp(modulus, modulus, static_cast<mp_bitcnt_t>(size * 8));
		mpz_sub(value, value, modulus);
		mpz_clear(modulus);
	}
	return wrap_bigint(value);
#endif
}

BigIntHandle *bigint_constant(int64_t val) {
#ifndef BIGMATH_HAS_GMP
	(void)val;
	return nullptr;
#else
	auto create = [](int64_t constant) {
		mpz_ptr value = nullptr;
		bigint_backend_from_long(&value, constant);
		return wrap_bigint(value, true);
	};
	static BigIntHandle *negative_one = create(-1);
	static BigIntHandle *zero = create(0);
	static BigIntHandle *one = create(1);
	static BigIntHandle *two = create(2);
	static BigIntHandle *ten = create(10);
	switch (val) {
		case -1: return negative_one;
		case 0: return zero;
		case 1: return one;
		case 2: return two;
		case 10: return ten;
		default: return nullptr;
	}
#endif
}

BigIntHandle *bigint_copy(BigIntHandle *value) {
	if (value == nullptr || value->value == nullptr) {
		return nullptr;
	}
	mpz_ptr result = nullptr;
	bigint_backend_init(&result);
	if (result != nullptr) {
		bigint_backend_set(result, value->value);
	}
	return wrap_bigint(result);
}

int bigint_set(BigIntHandle *out, BigIntHandle *value) {
	if (!mutable_bigint(out) || value == nullptr || value->value == nullptr) return -1;
	bigint_backend_set(out->value, value->value);
	out->version++;
	return 0;
}

int bigint_set_long(BigIntHandle *out, int64_t val) {
	if (!mutable_bigint(out)) return -1;
	bigint_backend_set_long(out->value, val);
	out->version++;
	return 0;
}

int bigint_set_string(BigIntHandle *out, const char *str, int radix) {
	if (!mutable_bigint(out) || str == nullptr) return -1;
	bigint_backend_set_string(out->value, str, radix);
	out->version++;
	return 0;
}

#define BIGINT_BINARY_WRAPPER(name, raw) \
	BigIntHandle *name(BigIntHandle *a, BigIntHandle *b) { \
		if (a == nullptr || b == nullptr || a->value == nullptr || b->value == nullptr) return nullptr; \
		mpz_ptr result = nullptr; \
		raw(&result, a->value, b->value); \
		return wrap_bigint(result); \
	}

#define BIGINT_UNARY_WRAPPER(name, raw) \
	BigIntHandle *name(BigIntHandle *a) { \
		if (a == nullptr || a->value == nullptr) return nullptr; \
		mpz_ptr result = nullptr; \
		raw(&result, a->value); \
		return wrap_bigint(result); \
	}

BIGINT_BINARY_WRAPPER(bigint_add, bigint_backend_add)
BIGINT_BINARY_WRAPPER(bigint_sub, bigint_backend_sub)
BIGINT_BINARY_WRAPPER(bigint_mul_cpu, bigint_backend_mul_cpu)
BIGINT_BINARY_WRAPPER(bigint_mul_gmp, bigint_backend_mul_gmp)
BIGINT_BINARY_WRAPPER(bigint_div, bigint_backend_div)
BIGINT_BINARY_WRAPPER(bigint_mod, bigint_backend_mod)
BIGINT_BINARY_WRAPPER(bigint_gcd, bigint_backend_gcd)
BIGINT_BINARY_WRAPPER(bigint_lcm, bigint_backend_lcm)
BIGINT_BINARY_WRAPPER(bigint_and, bigint_backend_and)
BIGINT_BINARY_WRAPPER(bigint_or, bigint_backend_or)
BIGINT_BINARY_WRAPPER(bigint_xor, bigint_backend_xor)
BIGINT_UNARY_WRAPPER(bigint_neg, bigint_backend_neg)
BIGINT_UNARY_WRAPPER(bigint_abs, bigint_backend_abs)
BIGINT_UNARY_WRAPPER(bigint_sqrt, bigint_backend_sqrt)
BIGINT_UNARY_WRAPPER(bigint_next_prime, bigint_backend_next_prime)

#undef BIGINT_BINARY_WRAPPER
#undef BIGINT_UNARY_WRAPPER

BigIntHandle *bigint_mul(BigIntHandle *a, BigIntHandle *b) {
	if (a == nullptr || b == nullptr || a->value == nullptr || b->value == nullptr) return nullptr;
	const ProductCacheKey cache_key = product_cache_key(
		a,
		b,
		bigmath::caching::PRODUCT_CONFIG_BIGINT_AUTO
	);
	mpz_ptr result = nullptr;
	bigint_backend_mul(&result, a->value, b->value, &cache_key);
	return wrap_bigint(result);
}

BigIntHandle *bigint_pow(BigIntHandle *a, uint64_t exp) {
	if (a == nullptr || a->value == nullptr) return nullptr;
	mpz_ptr result = nullptr;
	bigint_backend_pow(&result, a->value, exp);
	return wrap_bigint(result);
}

BigIntHandle *bigint_powm(BigIntHandle *base, BigIntHandle *exp, BigIntHandle *mod) {
	if (base == nullptr || exp == nullptr || mod == nullptr ||
			base->value == nullptr || exp->value == nullptr || mod->value == nullptr) return nullptr;
	mpz_ptr result = nullptr;
	bigint_backend_powm(&result, base->value, exp->value, mod->value);
	return wrap_bigint(result);
}

BigIntHandle *bigint_powm_gmp(BigIntHandle *base, BigIntHandle *exp, BigIntHandle *mod) {
	if (base == nullptr || exp == nullptr || mod == nullptr ||
			base->value == nullptr || exp->value == nullptr || mod->value == nullptr) return nullptr;
	mpz_ptr result = nullptr;
	bigint_backend_powm_gmp(&result, base->value, exp->value, mod->value);
	return wrap_bigint(result);
}

BigIntHandle *bigint_shl(BigIntHandle *a, uint64_t bits) {
	if (a == nullptr || a->value == nullptr) return nullptr;
	mpz_ptr result = nullptr;
	bigint_backend_shl(&result, a->value, bits);
	return wrap_bigint(result);
}

BigIntHandle *bigint_shr(BigIntHandle *a, uint64_t bits) {
	if (a == nullptr || a->value == nullptr) return nullptr;
	mpz_ptr result = nullptr;
	bigint_backend_shr(&result, a->value, bits);
	return wrap_bigint(result);
}

BigIntHandle *bigint_factorial(uint64_t n) {
	mpz_ptr result = nullptr;
	bigint_backend_factorial(&result, n);
	return wrap_bigint(result);
}

int bigint_add_into(BigIntHandle *out, BigIntHandle *a, BigIntHandle *b) {
	if (!mutable_bigint(out) || a == nullptr || b == nullptr) return -1;
	bigint_backend_add_into(out->value, a->value, b->value);
	out->version++;
	return 0;
}

int bigint_mul_into(BigIntHandle *out, BigIntHandle *a, BigIntHandle *b) {
	if (!mutable_bigint(out) || a == nullptr || b == nullptr) return -1;
	const ProductCacheKey cache_key = product_cache_key(
		a,
		b,
		bigmath::caching::PRODUCT_CONFIG_BIGINT_AUTO
	);
	bigint_backend_mul_into(out->value, a->value, b->value, &cache_key);
	out->version++;
	return 0;
}

int bigint_div_into(BigIntHandle *out, BigIntHandle *a, BigIntHandle *b) {
	if (!mutable_bigint(out) || a == nullptr || b == nullptr) return -1;
	bigint_backend_div_into(out->value, a->value, b->value);
	out->version++;
	return 0;
}

int bigint_sqrt_into(BigIntHandle *out, BigIntHandle *a) {
	if (!mutable_bigint(out) || a == nullptr) return -1;
	bigint_backend_sqrt_into(out->value, a->value);
	out->version++;
	return 0;
}

int bigint_cmp(BigIntHandle *a, BigIntHandle *b) {
	return a == nullptr || b == nullptr ? 0 : bigint_backend_cmp(a->value, b->value);
}

int bigint_sign(BigIntHandle *a) {
	return a == nullptr ? 0 : bigint_backend_sign(a->value);
}

int bigint_is_probably_prime(BigIntHandle *a, int reps) {
	return a == nullptr ? 0 : bigint_backend_is_probably_prime(a->value, reps);
}

int64_t bigint_to_long(BigIntHandle *a) {
	return a == nullptr ? 0 : bigint_backend_to_long(a->value);
}

double bigint_to_double(BigIntHandle *a) {
	return a == nullptr ? 0.0 : bigint_backend_to_double(a->value);
}

char *bigint_to_string(BigIntHandle *a, int radix) {
	return a == nullptr ? nullptr : bigint_backend_to_string(a->value, radix);
}

uint64_t bigint_twos_complement_size(BigIntHandle *a) {
#ifndef BIGMATH_HAS_GMP
	(void)a;
	return 0;
#else
	if (a == nullptr) return 0;
	try {
		return static_cast<uint64_t>(export_twos_complement(a->value).size());
	} catch (...) {
		return 0;
	}
#endif
}

int bigint_to_twos_complement(BigIntHandle *a, uint8_t *out, uint64_t size) {
#ifndef BIGMATH_HAS_GMP
	(void)a;
	(void)out;
	(void)size;
	return -1;
#else
	if (a == nullptr || out == nullptr) return -1;
	try {
		const std::vector<uint8_t> bytes = export_twos_complement(a->value);
		if (size != bytes.size()) return -1;
		std::memcpy(out, bytes.data(), bytes.size());
		return 0;
	} catch (...) {
		return -1;
	}
#endif
}

uint64_t bigint_id(BigIntHandle *a) {
	return a == nullptr ? 0 : a->id;
}

uint64_t bigint_version(BigIntHandle *a) {
	return a == nullptr ? 0 : a->version;
}

void bigint_free(BigIntHandle *a) {
	if (a == nullptr || a->read_only) return;
	bigint_backend_free(a->value);
	a->value = nullptr;
	delete a;
}

void bigint_free_string(char *value) {
	bigint_backend_free_string(value);
}

BigDeciHandle *bigdecimal_from_double(double val, int precision) {
	mpfr_ptr value = nullptr;
	bigdecimal_backend_from_double(&value, val, precision);
	return wrap_bigdecimal(value);
}

BigDeciHandle *bigdecimal_from_string(const char *str, int precision) {
	mpfr_ptr value = nullptr;
	bigdecimal_backend_from_string(&value, str, precision);
	return wrap_bigdecimal(value);
}

BigDeciHandle *bigdecimal_from_bigint(BigIntHandle *val, int precision) {
	if (val == nullptr || val->value == nullptr) return nullptr;
	mpfr_ptr value = nullptr;
	bigdecimal_backend_from_bigint(&value, val->value, precision);
	return wrap_bigdecimal(value);
}

BigDeciHandle *bigdecimal_constant(double val, int precision) {
#ifndef BIGMATH_HAS_GMP
	(void)val;
	(void)precision;
	return nullptr;
#else
	auto create = [precision](double constant) {
		mpfr_ptr value = nullptr;
		bigdecimal_backend_from_double(&value, constant, precision);
		return wrap_bigdecimal(value, true);
	};
	static BigDeciHandle *negative_one = create(-1.0);
	static BigDeciHandle *zero = create(0.0);
	static BigDeciHandle *one = create(1.0);
	static BigDeciHandle *two = create(2.0);
	static BigDeciHandle *ten = create(10.0);
	if (val == -1.0) return negative_one;
	if (val == 0.0) return zero;
	if (val == 1.0) return one;
	if (val == 2.0) return two;
	if (val == 10.0) return ten;
	return nullptr;
#endif
}

BigDeciHandle *bigdecimal_copy(BigDeciHandle *source) {
	if (source == nullptr || source->value == nullptr) return nullptr;
	mpfr_ptr value = nullptr;
	bigdecimal_backend_from_double(&value, 0.0, 2);
	if (value != nullptr) {
		bigdecimal_backend_set(value, source->value);
	}
	return wrap_bigdecimal(value);
}

int bigdecimal_set(BigDeciHandle *out, BigDeciHandle *value) {
	if (!mutable_bigdecimal(out) || value == nullptr || value->value == nullptr) return -1;
	bigdecimal_backend_set(out->value, value->value);
	out->version++;
	return 0;
}

int bigdecimal_set_double(BigDeciHandle *out, double val) {
	if (!mutable_bigdecimal(out)) return -1;
	bigdecimal_backend_set_double(out->value, val);
	out->version++;
	return 0;
}

int bigdecimal_set_string(BigDeciHandle *out, const char *str, int precision) {
	if (!mutable_bigdecimal(out) || str == nullptr) return -1;
	bigdecimal_backend_set_string(out->value, str, precision);
	out->version++;
	return 0;
}

#define BIGDECIMAL_BINARY_WRAPPER(name, raw) \
	BigDeciHandle *name(BigDeciHandle *a, BigDeciHandle *b) { \
		if (a == nullptr || b == nullptr || a->value == nullptr || b->value == nullptr) return nullptr; \
		mpfr_ptr result = nullptr; \
		raw(&result, a->value, b->value); \
		return wrap_bigdecimal(result); \
	}

#define BIGDECIMAL_UNARY_WRAPPER(name, raw) \
	BigDeciHandle *name(BigDeciHandle *a) { \
		if (a == nullptr || a->value == nullptr) return nullptr; \
		mpfr_ptr result = nullptr; \
		raw(&result, a->value); \
		return wrap_bigdecimal(result); \
	}

BIGDECIMAL_BINARY_WRAPPER(bigdecimal_add, bigdecimal_backend_add)
BIGDECIMAL_BINARY_WRAPPER(bigdecimal_sub, bigdecimal_backend_sub)
BIGDECIMAL_BINARY_WRAPPER(bigdecimal_mul_mpfr, bigdecimal_backend_mul_mpfr)
BIGDECIMAL_BINARY_WRAPPER(bigdecimal_div, bigdecimal_backend_div)
BIGDECIMAL_BINARY_WRAPPER(bigdecimal_pow, bigdecimal_backend_pow)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_neg, bigdecimal_backend_neg)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_abs, bigdecimal_backend_abs)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_sqrt, bigdecimal_backend_sqrt)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_log, bigdecimal_backend_log)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_exp, bigdecimal_backend_exp)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_sin, bigdecimal_backend_sin)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_cos, bigdecimal_backend_cos)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_tan, bigdecimal_backend_tan)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_ceil, bigdecimal_backend_ceil)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_floor, bigdecimal_backend_floor)
BIGDECIMAL_UNARY_WRAPPER(bigdecimal_round, bigdecimal_backend_round)

#undef BIGDECIMAL_BINARY_WRAPPER
#undef BIGDECIMAL_UNARY_WRAPPER

BigDeciHandle *bigdecimal_mul(BigDeciHandle *a, BigDeciHandle *b) {
	if (a == nullptr || b == nullptr || a->value == nullptr || b->value == nullptr) return nullptr;
	const ProductCacheKey cache_key = product_cache_key(
		a,
		b,
		bigmath::caching::PRODUCT_CONFIG_BIGDECI_AUTO
	);
	mpfr_ptr result = nullptr;
	bigdecimal_backend_mul(&result, a->value, b->value, &cache_key);
	return wrap_bigdecimal(result);
}

BigDeciHandle *bigdecimal_pow_long(BigDeciHandle *a, int64_t exp) {
	if (a == nullptr || a->value == nullptr) return nullptr;
	mpfr_ptr result = nullptr;
	bigdecimal_backend_pow_long(&result, a->value, exp);
	return wrap_bigdecimal(result);
}

int bigdecimal_add_into(BigDeciHandle *out, BigDeciHandle *a, BigDeciHandle *b) {
	if (!mutable_bigdecimal(out) || a == nullptr || b == nullptr) return -1;
	bigdecimal_backend_add_into(out->value, a->value, b->value);
	out->version++;
	return 0;
}

int bigdecimal_mul_into(BigDeciHandle *out, BigDeciHandle *a, BigDeciHandle *b) {
	if (!mutable_bigdecimal(out) || a == nullptr || b == nullptr) return -1;
	const ProductCacheKey cache_key = product_cache_key(
		a,
		b,
		bigmath::caching::PRODUCT_CONFIG_BIGDECI_AUTO
	);
	bigdecimal_backend_mul_into(out->value, a->value, b->value, &cache_key);
	out->version++;
	return 0;
}

int bigdecimal_div_into(BigDeciHandle *out, BigDeciHandle *a, BigDeciHandle *b) {
	if (!mutable_bigdecimal(out) || a == nullptr || b == nullptr) return -1;
	bigdecimal_backend_div_into(out->value, a->value, b->value);
	out->version++;
	return 0;
}

int bigdecimal_sqrt_into(BigDeciHandle *out, BigDeciHandle *a) {
	if (!mutable_bigdecimal(out) || a == nullptr) return -1;
	bigdecimal_backend_sqrt_into(out->value, a->value);
	out->version++;
	return 0;
}

int bigdecimal_cmp(BigDeciHandle *a, BigDeciHandle *b) {
	return a == nullptr || b == nullptr ? 0 : bigdecimal_backend_cmp(a->value, b->value);
}

double bigdecimal_to_double(BigDeciHandle *a) {
	return a == nullptr ? 0.0 : bigdecimal_backend_to_double(a->value);
}

char *bigdecimal_to_string(BigDeciHandle *a) {
	return a == nullptr ? nullptr : bigdecimal_backend_to_string(a->value);
}

uint64_t bigdecimal_id(BigDeciHandle *a) {
	return a == nullptr ? 0 : a->id;
}

uint64_t bigdecimal_version(BigDeciHandle *a) {
	return a == nullptr ? 0 : a->version;
}

void bigdecimal_free(BigDeciHandle *a) {
	if (a == nullptr || a->read_only) return;
	bigdecimal_backend_free(a->value);
	a->value = nullptr;
	delete a;
}

void bigdecimal_free_string(char *value) {
	bigdecimal_backend_free_string(value);
}
