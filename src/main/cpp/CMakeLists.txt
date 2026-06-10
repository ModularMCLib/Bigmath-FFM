if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
	cmake_minimum_required(VERSION 3.21)
	set(BIGMATH_STANDALONE_CUDA_PLATFORM OFF)
	if((CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows" OR CMAKE_HOST_SYSTEM_NAME STREQUAL "Linux")
			AND CMAKE_HOST_SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64|AMD64|X86_64)$"
			AND NOT CMAKE_TOOLCHAIN_FILE MATCHES "android.toolchain.cmake"
			AND NOT DEFINED ANDROID_ABI)
		set(BIGMATH_STANDALONE_CUDA_PLATFORM ON)
	endif()
	if(BIGMATH_STANDALONE_CUDA_PLATFORM)
		if(DEFINED ENV{BIGMATH_CUDA_ARCH} AND NOT "$ENV{BIGMATH_CUDA_ARCH}" STREQUAL "")
			set(CMAKE_CUDA_ARCHITECTURES "$ENV{BIGMATH_CUDA_ARCH}" CACHE STRING "CUDA architectures" FORCE)
		elseif(NOT DEFINED CMAKE_CUDA_ARCHITECTURES)
			set(CMAKE_CUDA_ARCHITECTURES native CACHE STRING "CUDA architectures")
		endif()
		project(bigmath-ffm-native VERSION 0.1.0 LANGUAGES CXX CUDA)
	else()
		project(bigmath-ffm-native VERSION 0.1.0 LANGUAGES CXX)
	endif()
endif()

option(USE_GMP "Build with GMP/MPFR native bigint/bigdecimal support" ON)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(WIN32 AND NOT MSVC)
	message(FATAL_ERROR "Windows native builds on this experimental CUDA branch require MSVC")
endif()

set(BIGMATH_CUDA_SUPPORTED_PLATFORM OFF)
if((WIN32 OR (UNIX AND NOT APPLE AND NOT ANDROID))
		AND CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64|AMD64|X86_64)$")
	set(BIGMATH_CUDA_SUPPORTED_PLATFORM ON)
endif()

if(BIGMATH_CUDA_SUPPORTED_PLATFORM)
	if(DEFINED ENV{BIGMATH_CUDA_ARCH} AND NOT "$ENV{BIGMATH_CUDA_ARCH}" STREQUAL "")
		set(CMAKE_CUDA_ARCHITECTURES "$ENV{BIGMATH_CUDA_ARCH}" CACHE STRING "CUDA architectures" FORCE)
	endif()
	if(NOT DEFINED CMAKE_CUDA_ARCHITECTURES)
		set(CMAKE_CUDA_ARCHITECTURES native CACHE STRING "CUDA architectures")
	endif()
	enable_language(CUDA)
	find_package(CUDAToolkit REQUIRED)
	set(CMAKE_CUDA_STANDARD 17)
	set(CMAKE_CUDA_STANDARD_REQUIRED ON)
endif()

if(USE_GMP)
	if(WIN32)
		find_path(GMP_INCLUDE_DIR
			NAMES gmp.h
			PATH_SUFFIXES include
		)
		find_library(GMP_LIBRARY
			NAMES gmp libgmp gmp-10 libgmp-10
			PATH_SUFFIXES lib
		)
		find_file(GMP_RUNTIME
			NAMES gmp-10.dll libgmp-10.dll gmp.dll libgmp.dll
			PATH_SUFFIXES bin lib
		)
		if(GMP_INCLUDE_DIR AND GMP_LIBRARY)
			add_library(GMP::GMP SHARED IMPORTED)
			set_target_properties(GMP::GMP PROPERTIES
				IMPORTED_IMPLIB "${GMP_LIBRARY}"
				INTERFACE_INCLUDE_DIRECTORIES "${GMP_INCLUDE_DIR}"
			)
			if(GMP_RUNTIME)
				set_target_properties(GMP::GMP PROPERTIES IMPORTED_LOCATION "${GMP_RUNTIME}")
			endif()
			set(GMP_FOUND TRUE)
		endif()

		find_path(MPFR_INCLUDE_DIR
			NAMES mpfr.h
			PATH_SUFFIXES include
		)
		find_library(MPFR_LIBRARY
			NAMES mpfr libmpfr mpfr-6 libmpfr-6
			PATH_SUFFIXES lib
		)
		find_file(MPFR_RUNTIME
			NAMES mpfr-6.dll libmpfr-6.dll mpfr.dll libmpfr.dll
			PATH_SUFFIXES bin lib
		)
		if(MPFR_INCLUDE_DIR AND MPFR_LIBRARY)
			add_library(MPFR::MPFR SHARED IMPORTED)
			set_target_properties(MPFR::MPFR PROPERTIES
				IMPORTED_IMPLIB "${MPFR_LIBRARY}"
				INTERFACE_INCLUDE_DIRECTORIES "${MPFR_INCLUDE_DIR}"
			)
			if(MPFR_RUNTIME)
				set_target_properties(MPFR::MPFR PROPERTIES IMPORTED_LOCATION "${MPFR_RUNTIME}")
			endif()
			set(MPFR_FOUND TRUE)
		endif()
	elseif(UNIX AND NOT APPLE AND NOT ANDROID)
		find_path(GMP_INCLUDE_DIR
			NAMES gmp.h
			PATH_SUFFIXES include
		)
		find_library(GMP_LIBRARY
			NAMES gmp libgmp
			PATH_SUFFIXES lib lib64
		)
		find_file(GMP_RUNTIME
			NAMES libgmp.so.10 libgmp.so
			PATH_SUFFIXES lib lib64
		)
		if(GMP_INCLUDE_DIR AND GMP_LIBRARY AND GMP_RUNTIME)
			add_library(GMP::GMP SHARED IMPORTED)
			set_target_properties(GMP::GMP PROPERTIES
				IMPORTED_LOCATION "${GMP_RUNTIME}"
				INTERFACE_INCLUDE_DIRECTORIES "${GMP_INCLUDE_DIR}"
			)
			set(GMP_FOUND TRUE)
		endif()

		find_path(MPFR_INCLUDE_DIR
			NAMES mpfr.h
			PATH_SUFFIXES include
		)
		find_library(MPFR_LIBRARY
			NAMES mpfr libmpfr
			PATH_SUFFIXES lib lib64
		)
		find_file(MPFR_RUNTIME
			NAMES libmpfr.so.6 libmpfr.so
			PATH_SUFFIXES lib lib64
		)
		if(MPFR_INCLUDE_DIR AND MPFR_LIBRARY AND MPFR_RUNTIME)
			add_library(MPFR::MPFR SHARED IMPORTED)
			set_target_properties(MPFR::MPFR PROPERTIES
				IMPORTED_LOCATION "${MPFR_RUNTIME}"
				INTERFACE_INCLUDE_DIRECTORIES "${MPFR_INCLUDE_DIR}"
			)
			set(MPFR_FOUND TRUE)
		endif()
	endif()

	if(NOT GMP_FOUND)
		find_package(GMP QUIET)
	endif()
	if(NOT MPFR_FOUND)
		find_package(MPFR QUIET)
	endif()

	if(NOT GMP_FOUND OR NOT MPFR_FOUND)
		find_package(PkgConfig QUIET)
	endif()
	if(PkgConfig_FOUND AND (NOT GMP_FOUND OR NOT MPFR_FOUND))
		if(NOT GMP_FOUND)
			pkg_check_modules(GMP gmp IMPORTED_TARGET)
		endif()
		if(NOT MPFR_FOUND)
			pkg_check_modules(MPFR mpfr IMPORTED_TARGET)
		endif()
		if(GMP_FOUND AND NOT TARGET GMP::GMP)
			add_library(GMP::GMP ALIAS PkgConfig::GMP)
		endif()
		if(MPFR_FOUND AND NOT TARGET MPFR::MPFR)
			add_library(MPFR::MPFR ALIAS PkgConfig::MPFR)
		endif()
	endif()
endif()

# Always add all sources so CLion indexes every file
add_library(bigmath_ffm SHARED
	bigmath_ffm.cpp
	bigint.cpp
	bigdecimal.cpp
	int128.cpp
	algos.cpp
	ntt.cpp
	cuda_runtime_state.cpp
)
target_include_directories(bigmath_ffm PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

if(BIGMATH_CUDA_SUPPORTED_PLATFORM)
	target_sources(bigmath_ffm PRIVATE cuda_convolution.cu)
	target_compile_definitions(bigmath_ffm PRIVATE BIGMATH_HAS_CUDA)
	target_link_libraries(bigmath_ffm PRIVATE CUDA::cudart CUDA::cufft)
	set_source_files_properties(cuda_convolution.cu PROPERTIES LANGUAGE CUDA)
endif()

if(WIN32)
	set_target_properties(bigmath_ffm PROPERTIES PREFIX "")
endif()

if(GMP_FOUND AND MPFR_FOUND)
	target_link_libraries(bigmath_ffm PRIVATE MPFR::MPFR GMP::GMP)
	target_compile_definitions(bigmath_ffm PRIVATE BIGMATH_HAS_GMP)
	if(UNIX AND NOT APPLE AND NOT ANDROID AND CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
		target_link_options(bigmath_ffm PRIVATE -static-libstdc++ -static-libgcc)
	endif()
	if(APPLE)
		set_target_properties(bigmath_ffm PROPERTIES
			INSTALL_RPATH "@loader_path"
			BUILD_WITH_INSTALL_RPATH TRUE
		)
	elseif(UNIX)
		set_target_properties(bigmath_ffm PROPERTIES
			INSTALL_RPATH "$ORIGIN"
			BUILD_WITH_INSTALL_RPATH TRUE
		)
	endif()
else()
	target_compile_definitions(bigmath_ffm PRIVATE BIGMATH_NO_GMP)
	if(NOT USE_GMP)
		message(STATUS "GMP/MPFR disabled by USE_GMP=OFF – building stubs only")
	else()
		message(STATUS "GMP/MPFR not found – building stubs only. "
			"Install GMP/MPFR and reconfigure to enable BigInt/BigDecimal")
	endif()
endif()

install(TARGETS bigmath_ffm
	LIBRARY DESTINATION lib
	RUNTIME DESTINATION lib
)

if(WIN32 AND GMP_FOUND AND MPFR_FOUND)
	install(FILES
		$<TARGET_RUNTIME_DLLS:bigmath_ffm>
		DESTINATION lib
	)
endif()

if(UNIX AND NOT APPLE AND NOT ANDROID AND GMP_FOUND AND MPFR_FOUND)
	install(FILES
		${GMP_RUNTIME}
		${MPFR_RUNTIME}
		DESTINATION lib
		OPTIONAL
	)
endif()
