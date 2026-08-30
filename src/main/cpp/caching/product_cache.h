#ifndef BIGMATH_PRODUCT_CACHE_H
#define BIGMATH_PRODUCT_CACHE_H

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace bigmath::caching {

inline constexpr size_t PRODUCT_CACHE_MAX_RESULT_BYTES = 64 * 1024 * 1024;
inline constexpr uint64_t PRODUCT_CONFIG_BIGINT_AUTO = 1;
inline constexpr uint64_t PRODUCT_CONFIG_BIGDECI_AUTO = 2;

constexpr bool product_result_fits(size_t limb_count) {
	return limb_count <= PRODUCT_CACHE_MAX_RESULT_BYTES / sizeof(uint64_t);
}

struct ProductOperandKey {
	uint64_t id;
	uint64_t version;
};

struct ProductCacheKey {
	ProductOperandKey left;
	ProductOperandKey right;
	uint64_t config;

	static ProductCacheKey canonical(
		ProductOperandKey left,
		ProductOperandKey right,
		uint64_t config
	);

	bool operator==(const ProductCacheKey &other) const;
};

struct ProductCacheLookup {
	bool hit = false;
	bool admit = false;
	std::shared_ptr<const std::vector<uint64_t>> packed_limbs;
};

struct ProductCacheMetrics {
	uint64_t hits;
	uint64_t misses;
	uint64_t admissions;
	uint64_t evictions;
	uint64_t bypasses;
	uint64_t bytes;
};

ProductCacheLookup lookup_product(const ProductCacheKey &key);
void store_product(
	const ProductCacheKey &key,
	std::vector<uint64_t> packed_limbs,
	bool admitted
);
ProductCacheMetrics product_cache_metrics();
void record_product_cache_bypass();

}

#endif
