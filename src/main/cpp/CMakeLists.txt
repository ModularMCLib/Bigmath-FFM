option(USE_GMP "Build with GMP/MPFR native bigint/bigdecimal support" ON)

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
		message(WARNING "GMP/MPFR not found – building int128 only. "
			"Install GMP/MPFR or set -DUSE_GMP=OFF to suppress this warning")
	endif()
endif()
