#include "product_cache.h"

#include <algorithm>
#include <array>
#include <memory>
#include <mutex>
#include <new>
#include <utility>

namespace bigmath::caching {
namespace {

constexpr size_t MAX_RESULTS = 16;
constexpr size_t ADMISSION_ENTRIES = 64;

bool operand_less(const ProductOperandKey &left, const ProductOperandKey &right) {
	return left.id < right.id || (left.id == right.id && left.version < right.version);
}

struct CachedProduct {
	bool ready = false;
	ProductCacheKey key{};
	uint64_t last_used = 0;
	size_t bytes = 0;
	std::shared_ptr<const std::vector<uint64_t>> packed_limbs;
};

struct AdmissionCandidate {
	bool ready = false;
	ProductCacheKey key{};
	uint64_t last_used = 0;
};

class ProductCache {
public:
	ProductCacheLookup lookup(const ProductCacheKey &key) {
		std::lock_guard lock(mutex_);
		if (!enabled_) return ProductCacheLookup{};
		const uint64_t use_tick = next_tick();
		for (CachedProduct &entry : results_) {
			if (entry.ready && entry.key == key) {
				ProductCacheLookup result;
				result.packed_limbs = entry.packed_limbs;
				result.hit = true;
				entry.last_used = use_tick;
				metrics_.hits++;
				return result;
			}
		}

		metrics_.misses++;
		for (AdmissionCandidate &candidate : candidates_) {
			if (candidate.ready && candidate.key == key) {
				candidate = AdmissionCandidate{};
				return ProductCacheLookup{false, true, {}};
			}
		}

		AdmissionCandidate &candidate = admission_target();
		candidate.ready = true;
		candidate.key = key;
		candidate.last_used = use_tick;
		return ProductCacheLookup{};
	}

	void store(
			const ProductCacheKey &key,
			std::vector<uint64_t> packed_limbs,
			bool admitted
	) {
		if (!admitted || !accepts(packed_limbs.capacity())) {
			return;
		}
		const size_t result_bytes = packed_limbs.capacity() * sizeof(uint64_t);
		auto shared_limbs = std::make_shared<const std::vector<uint64_t>>(std::move(packed_limbs));
		std::lock_guard lock(mutex_);
		if (!enabled_ || max_results_ == 0 ||
				result_bytes > max_bytes_) {
			return;
		}
		const uint64_t use_tick = next_tick();
		for (CachedProduct &entry : results_) {
			if (entry.ready && entry.key == key) {
				entry.last_used = use_tick;
				return;
			}
		}

		while (result_count_ >= max_results_ || bytes_ > max_bytes_ - result_bytes) {
			if (!evict_lru()) {
				return;
			}
		}

		for (CachedProduct &entry : results_) {
			if (!entry.ready) {
				entry.ready = true;
				entry.key = key;
				entry.last_used = use_tick;
				entry.bytes = result_bytes;
				entry.packed_limbs = std::move(shared_limbs);
				result_count_++;
				bytes_ += result_bytes;
				metrics_.admissions++;
				metrics_.bytes = bytes_;
				return;
			}
		}
	}

	ProductCacheMetrics metrics() const {
		std::lock_guard lock(mutex_);
		return metrics_;
	}

	bool accepts(size_t limb_count) const {
		std::lock_guard lock(mutex_);
		return enabled_ && max_results_ != 0 && limb_count <= max_bytes_ / sizeof(uint64_t);
	}

	void configure(bool enabled, size_t max_entries, size_t max_bytes) {
		std::lock_guard lock(mutex_);
		enabled_ = enabled;
		max_results_ = std::min(max_entries, MAX_RESULTS);
		max_bytes_ = max_bytes;
		for (CachedProduct &entry : results_) entry = CachedProduct{};
		for (AdmissionCandidate &candidate : candidates_) candidate = AdmissionCandidate{};
		result_count_ = 0;
		bytes_ = 0;
		metrics_.bytes = 0;
	}

	void record_bypass() {
		std::lock_guard lock(mutex_);
		metrics_.bypasses++;
	}

private:
	uint64_t next_tick() {
		tick_++;
		if (tick_ == 0) {
			tick_ = 1;
			for (CachedProduct &entry : results_) entry.last_used = 0;
			for (AdmissionCandidate &candidate : candidates_) candidate.last_used = 0;
		}
		return tick_;
	}

	AdmissionCandidate &admission_target() {
		AdmissionCandidate *target = &candidates_.front();
		for (AdmissionCandidate &candidate : candidates_) {
			if (!candidate.ready) return candidate;
			if (candidate.last_used < target->last_used) target = &candidate;
		}
		return *target;
	}

	bool evict_lru() {
		CachedProduct *target = nullptr;
		for (CachedProduct &entry : results_) {
			if (!entry.ready || entry.packed_limbs.use_count() > 1) continue;
			if (target == nullptr || entry.last_used < target->last_used) target = &entry;
		}
		if (target == nullptr) return false;
		bytes_ -= target->bytes;
		*target = CachedProduct{};
		result_count_--;
		metrics_.evictions++;
		metrics_.bytes = bytes_;
		return true;
	}

	mutable std::mutex mutex_;
	std::array<CachedProduct, MAX_RESULTS> results_{};
	std::array<AdmissionCandidate, ADMISSION_ENTRIES> candidates_{};
	uint64_t tick_ = 0;
	bool enabled_ = true;
	size_t max_results_ = MAX_RESULTS;
	size_t max_bytes_ = PRODUCT_CACHE_MAX_RESULT_BYTES;
	size_t result_count_ = 0;
	size_t bytes_ = 0;
	ProductCacheMetrics metrics_{};
};

ProductCache &cache() {
	static ProductCache instance;
	return instance;
}

}

ProductCacheKey ProductCacheKey::canonical(
		ProductOperandKey left,
		ProductOperandKey right,
		uint64_t config
) {
	if (operand_less(right, left)) std::swap(left, right);
	return ProductCacheKey{left, right, config};
}

bool ProductCacheKey::operator==(const ProductCacheKey &other) const {
	return left.id == other.left.id && left.version == other.left.version &&
		right.id == other.right.id && right.version == other.right.version &&
		config == other.config;
}

ProductCacheLookup lookup_product(const ProductCacheKey &key) {
	try {
		return cache().lookup(key);
	} catch (const std::bad_alloc &) {
		cache().record_bypass();
		return ProductCacheLookup{};
	}
}

bool product_result_fits(size_t limb_count) {
	return cache().accepts(limb_count);
}

void configure_product_cache(bool enabled, size_t max_entries, size_t max_bytes) {
	cache().configure(enabled, max_entries, max_bytes);
}

void store_product(
		const ProductCacheKey &key,
		std::vector<uint64_t> packed_limbs,
		bool admitted
) {
	cache().store(key, std::move(packed_limbs), admitted);
}

ProductCacheMetrics product_cache_metrics() {
	return cache().metrics();
}

void record_product_cache_bypass() {
	cache().record_bypass();
}

}
