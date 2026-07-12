cmake_minimum_required(VERSION 3.22.1)
project("dantotsuvips")

add_library(dantotsuvips SHARED
            vips-jni.cpp)

# Link against liblog and ndk graphics (for reading/writing bitmaps!)
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# Link against the precompiled libvips.so
add_library(vips SHARED IMPORTED)
set_target_properties(vips PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libvips.so)

# Include directories
target_include_directories(dantotsuvips PRIVATE
                           ${CMAKE_SOURCE_DIR}/../jniLibs/include)

target_link_libraries(dantotsuvips
                      vips
                      ${log-lib}
                      ${jnigraphics-lib})
