if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
	cmake_minimum_required(VERSION 3.21)
	project(bigmath-ffm-native VERSION 0.1.0 LANGUAGES CXX)
endif()

option(USE_GMP "Build with GMP/MPFR native bigint/bigdecimal support" ON)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

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
)
target_include_directories(bigmath_ffm PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

if(WIN32)
	set_target_properties(bigmath_ffm PROPERTIES PREFIX "")
endif()

if(GMP_FOUND AND MPFR_FOUND)
	target_link_libraries(bigmath_ffm PRIVATE GMP::GMP MPFR::MPFR)
	target_compile_definitions(bigmath_ffm PRIVATE BIGMATH_HAS_GMP)
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
