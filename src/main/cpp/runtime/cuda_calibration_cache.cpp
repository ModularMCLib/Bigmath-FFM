#include "cuda_calibration_cache.h"

#include <algorithm>
#include <array>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <limits>
#include <system_error>
#include <utility>
#include <vector>

#ifdef _WIN32
#define NOMINMAX
#include <io.h>
#include <windows.h>
#else
#include <fcntl.h>
#include <sys/file.h>
#include <unistd.h>
#endif

namespace bigmath::runtime {
namespace {

constexpr std::array<uint8_t, 8> CACHE_MAGIC = {'B', 'M', 'C', 'A', 'L', '0', '0', '1'};
constexpr uint32_t CACHE_SCHEMA_VERSION = 1;
constexpr uint64_t MAX_CACHE_BYTES = UINT64_C(65536);
constexpr uint32_t MAX_KEY_BYTES = 4096;

std::filesystem::path cache_directory() {
#ifdef _WIN32
	const DWORD length = GetEnvironmentVariableW(L"LOCALAPPDATA", nullptr, 0);
	if (length == 0) return {};
	std::vector<wchar_t> value(length);
	const DWORD written = GetEnvironmentVariableW(L"LOCALAPPDATA", value.data(), length);
	if (written == 0 || written >= length) return {};
	return std::filesystem::path(value.data()) / L"Bigmath-FFM";
#elif defined(__APPLE__)
	const char *home = std::getenv("HOME");
	if (home == nullptr || home[0] == '\0') return {};
	return std::filesystem::path(home) / "Library" / "Caches" / "Bigmath-FFM";
#else
	const char *xdg_cache = std::getenv("XDG_CACHE_HOME");
	if (xdg_cache != nullptr && xdg_cache[0] != '\0') {
		std::filesystem::path configured(xdg_cache);
		if (configured.is_absolute()) return configured / "bigmath-ffm";
	}
	const char *home = std::getenv("HOME");
	if (home == nullptr || home[0] == '\0') return {};
	return std::filesystem::path(home) / ".cache" / "bigmath-ffm";
#endif
}

void append_u8(std::vector<uint8_t> &bytes, uint8_t value) {
	bytes.push_back(value);
}

void append_u32(std::vector<uint8_t> &bytes, uint32_t value) {
	for (unsigned shift = 0; shift < 32; shift += 8) {
		bytes.push_back(static_cast<uint8_t>(value >> shift));
	}
}

void append_u64(std::vector<uint8_t> &bytes, uint64_t value) {
	for (unsigned shift = 0; shift < 64; shift += 8) {
		bytes.push_back(static_cast<uint8_t>(value >> shift));
	}
}

class Decoder final {
public:
	explicit Decoder(const std::vector<uint8_t> &bytes) : bytes_(bytes) {
	}

	bool read_u8(uint8_t &value) {
		if (offset_ >= bytes_.size()) return false;
		value = bytes_[offset_++];
		return true;
	}

	bool read_u32(uint32_t &value) {
		value = 0;
		for (unsigned shift = 0; shift < 32; shift += 8) {
			uint8_t byte = 0;
			if (!read_u8(byte)) return false;
			value |= static_cast<uint32_t>(byte) << shift;
		}
		return true;
	}

	bool read_u64(uint64_t &value) {
		value = 0;
		for (unsigned shift = 0; shift < 64; shift += 8) {
			uint8_t byte = 0;
			if (!read_u8(byte)) return false;
			value |= static_cast<uint64_t>(byte) << shift;
		}
		return true;
	}

	bool read_bytes(uint8_t *target, size_t size) {
		if (size > bytes_.size() - offset_) return false;
		if (size > 0) std::copy_n(bytes_.data() + offset_, size, target);
		offset_ += size;
		return true;
	}

	bool finished() const {
		return offset_ == bytes_.size();
	}

private:
	const std::vector<uint8_t> &bytes_;
	size_t offset_ = 0;
};

std::vector<uint8_t> encode(
		const std::string &key,
		const CudaCalibrationProfile &profile
) {
	std::vector<uint8_t> bytes;
	bytes.reserve(8192);
	bytes.insert(bytes.end(), CACHE_MAGIC.begin(), CACHE_MAGIC.end());
	append_u32(bytes, CACHE_SCHEMA_VERSION);
	append_u32(bytes, static_cast<uint32_t>(key.size()));
	bytes.insert(bytes.end(), key.begin(), key.end());
	append_u8(bytes, profile.completed ? 1 : 0);
	for (uint64_t threshold : profile.threshold_bits) append_u64(bytes, threshold);
	append_u64(bytes, profile.square_threshold_bits);
	append_u32(bytes, profile.ntt_transform_mask);
	for (const auto &shape : profile.cells) {
		for (const DispatchProfileCell &cell : shape) {
			append_u8(bytes, cell.measured ? 1 : 0);
			append_u8(bytes, static_cast<uint8_t>(cell.backend));
			append_u64(bytes, cell.cpu_nanos);
			append_u64(bytes, cell.cufft_cold_nanos);
			append_u64(bytes, cell.cufft_nanos);
			append_u64(bytes, cell.ntt_cold_nanos);
			append_u64(bytes, cell.ntt_nanos);
		}
	}
	return bytes;
}

bool decode(
		const std::vector<uint8_t> &bytes,
		const std::string &expected_key,
		CudaCalibrationProfile &profile
) {
	Decoder decoder(bytes);
	std::array<uint8_t, CACHE_MAGIC.size()> magic{};
	if (!decoder.read_bytes(magic.data(), magic.size()) || magic != CACHE_MAGIC) return false;
	uint32_t schema = 0;
	uint32_t key_size = 0;
	if (!decoder.read_u32(schema) || schema != CACHE_SCHEMA_VERSION ||
			!decoder.read_u32(key_size) || key_size > MAX_KEY_BYTES) {
		return false;
	}
	std::string key(key_size, '\0');
	if (!decoder.read_bytes(reinterpret_cast<uint8_t *>(key.data()), key.size()) ||
			key != expected_key) {
		return false;
	}

	CudaCalibrationProfile decoded;
	uint8_t completed = 0;
	if (!decoder.read_u8(completed) || completed != 1) return false;
	decoded.completed = true;
	for (uint64_t &threshold : decoded.threshold_bits) {
		if (!decoder.read_u64(threshold)) return false;
	}
	if (!decoder.read_u64(decoded.square_threshold_bits) ||
			!decoder.read_u32(decoded.ntt_transform_mask)) {
		return false;
	}
	for (auto &shape : decoded.cells) {
		for (DispatchProfileCell &cell : shape) {
			uint8_t measured = 0;
			uint8_t backend = 0;
			if (!decoder.read_u8(measured) || measured > 1 ||
					!decoder.read_u8(backend) ||
					backend > static_cast<uint8_t>(CalibratedBackend::NTT) ||
					!decoder.read_u64(cell.cpu_nanos) ||
					!decoder.read_u64(cell.cufft_cold_nanos) ||
					!decoder.read_u64(cell.cufft_nanos) ||
					!decoder.read_u64(cell.ntt_cold_nanos) ||
					!decoder.read_u64(cell.ntt_nanos)) {
				return false;
			}
			cell.measured = measured != 0;
			cell.backend = static_cast<CalibratedBackend>(backend);
		}
	}
	if (!decoder.finished()) return false;
	decoded.loaded_from_cache = true;
	profile = std::move(decoded);
	return true;
}

bool write_all(std::FILE *file, const std::vector<uint8_t> &bytes) {
	return bytes.empty() || std::fwrite(bytes.data(), 1, bytes.size(), file) == bytes.size();
}

bool write_atomically(
		const std::filesystem::path &data_path,
		const std::vector<uint8_t> &bytes
) {
	std::filesystem::path temporary = data_path;
	temporary += ".tmp";
	std::FILE *file = nullptr;
#ifdef _WIN32
	if (_wfopen_s(&file, temporary.c_str(), L"wb") != 0 || file == nullptr) return false;
#else
	file = std::fopen(temporary.c_str(), "wb");
	if (file == nullptr) return false;
#endif
	bool success = write_all(file, bytes) && std::fflush(file) == 0;
#ifdef _WIN32
	if (success) success = _commit(_fileno(file)) == 0;
#else
	if (success) success = fsync(fileno(file)) == 0;
#endif
	if (std::fclose(file) != 0) success = false;
	if (!success) {
		std::error_code ignored;
		std::filesystem::remove(temporary, ignored);
		return false;
	}

#ifdef _WIN32
	if (!MoveFileExW(
			temporary.c_str(),
			data_path.c_str(),
			MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH
	)) {
		DeleteFileW(temporary.c_str());
		return false;
	}
#else
	if (std::rename(temporary.c_str(), data_path.c_str()) != 0) {
		std::remove(temporary.c_str());
		return false;
	}
	int directory_fd = open(data_path.parent_path().c_str(), O_RDONLY);
	if (directory_fd >= 0) {
		fsync(directory_fd);
		close(directory_fd);
	}
#endif
	return true;
}

}

CudaCalibrationCache::CudaCalibrationCache(std::string key) : key_(std::move(key)) {
	if (key_.empty() || key_.size() > MAX_KEY_BYTES) return;
	const std::filesystem::path directory = cache_directory();
	if (directory.empty()) return;
	std::error_code error;
	std::filesystem::create_directories(directory, error);
	if (error) return;
	data_path_ = directory / "cuda-calibration-v1.bin";
	std::filesystem::path lock_path = data_path_;
	lock_path += ".lock";
#ifdef _WIN32
	HANDLE handle = INVALID_HANDLE_VALUE;
	while (handle == INVALID_HANDLE_VALUE) {
		handle = CreateFileW(
			lock_path.c_str(),
			GENERIC_READ | GENERIC_WRITE,
			0,
			nullptr,
			OPEN_ALWAYS,
			FILE_ATTRIBUTE_NORMAL,
			nullptr
		);
		if (handle != INVALID_HANDLE_VALUE) break;
		const DWORD error = GetLastError();
		if (error != ERROR_SHARING_VIOLATION && error != ERROR_LOCK_VIOLATION) return;
		Sleep(50);
	}
	lock_handle_ = handle;
#else
	int flags = O_CREAT | O_RDWR;
#ifdef O_CLOEXEC
	flags |= O_CLOEXEC;
#endif
	lock_fd_ = open(lock_path.c_str(), flags, 0600);
	if (lock_fd_ < 0) return;
	while (flock(lock_fd_, LOCK_EX) != 0) {
		if (errno == EINTR) continue;
		close(lock_fd_);
		lock_fd_ = -1;
		return;
	}
#endif
	locked_ = true;
}

CudaCalibrationCache::~CudaCalibrationCache() {
	if (!locked_) return;
#ifdef _WIN32
	CloseHandle(static_cast<HANDLE>(lock_handle_));
#else
	flock(lock_fd_, LOCK_UN);
	close(lock_fd_);
#endif
}

bool CudaCalibrationCache::load(CudaCalibrationProfile &profile) const {
	if (!locked_ || data_path_.empty()) return false;
	std::ifstream input(data_path_, std::ios::binary | std::ios::ate);
	if (!input) return false;
	const std::streamoff size = input.tellg();
	if (size <= 0 || static_cast<uint64_t>(size) > MAX_CACHE_BYTES) return false;
	std::vector<uint8_t> bytes(static_cast<size_t>(size));
	input.seekg(0, std::ios::beg);
	if (!input.read(
			reinterpret_cast<char *>(bytes.data()),
			static_cast<std::streamsize>(size)
	)) return false;
	return decode(bytes, key_, profile);
}

void CudaCalibrationCache::store(const CudaCalibrationProfile &profile) const {
	if (!locked_ || data_path_.empty() || !profile.completed) return;
	const std::vector<uint8_t> bytes = encode(key_, profile);
	write_atomically(data_path_, bytes);
}

}
