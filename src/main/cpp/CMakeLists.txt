find_package(GMP REQUIRED)
find_package(MPFR REQUIRED)

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
