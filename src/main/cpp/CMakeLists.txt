if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
	cmake_minimum_required(VERSION 3.20)
	project(bigmath-ffm-native VERSION 0.1.0 LANGUAGES CXX)
endif()

option(USE_GMP "Build with GMP/MPFR native bigint/bigdecimal support" ON)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(USE_GMP)
	find_package(GMP QUIET)
	find_package(MPFR QUIET)
endif()

if(GMP_FOUND AND MPFR_FOUND)
	add_library(bigmath_ffm SHARED
		bigmath_ffm.cpp
		bigint.cpp
		bigdecimal.cpp
		int128.cpp
	)
	target_include_directories(bigmath_ffm PRIVATE
		${CMAKE_CURRENT_SOURCE_DIR}
	)
	target_link_libraries(bigmath_ffm PRIVATE
		GMP::GMP
		MPFR::MPFR
	)
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
	install(TARGETS bigmath_ffm
		LIBRARY DESTINATION lib
		RUNTIME DESTINATION lib
	)
else()
	# CLion stub: build int128 only (no GMP/MPFR dependency)
	add_library(bigmath_ffm SHARED int128.cpp)
	target_include_directories(bigmath_ffm PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
	if(NOT USE_GMP)
		message(STATUS "GMP/MPFR disabled by USE_GMP=OFF – building int128 only")
	else()
		message(STATUS "GMP/MPFR not found – building int128 only. "
			"Install GMP/MPFR and reconfigure to enable BigInt/BigDecimal")
	endif()
endif()
