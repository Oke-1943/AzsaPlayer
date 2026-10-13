cmake_minimum_required(VERSION 3.22)
project(musicapp_native LANGUAGES C CXX)

# ---- TagLib 2.x（同梱ソースから静的ライブラリとしてビルド） ----
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
add_subdirectory(taglib)

# ---- JNIブリッジ ----
add_library(musicapp_native SHARED tagdump_jni.cpp)

get_target_property(TAGLIB_INCLUDE_DIRS tag INCLUDE_DIRECTORIES)
target_include_directories(musicapp_native PRIVATE
    ${TAGLIB_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib/taglib
    ${CMAKE_CURRENT_BINARY_DIR}/taglib
)
target_link_libraries(musicapp_native PRIVATE tag log)
