cmake_minimum_required(VERSION 3.16)
project(rafaelia_core C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

set(RAFAELIA_SOURCES
  raf_cpu_parser.c
  raf_perf.c
)

add_library(rafaelia_static STATIC ${RAFAELIA_SOURCES})
add_library(rafaelia_shared SHARED ${RAFAELIA_SOURCES})

set(RAFAELIA_WARNINGS -Wall -Wextra -Werror)

foreach(target rafaelia_static rafaelia_shared)
  target_include_directories(${target}
    PUBLIC
      $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
      $<INSTALL_INTERFACE:include>)
  target_compile_options(${target} PRIVATE ${RAFAELIA_WARNINGS})
  target_compile_definitions(${target} PRIVATE _POSIX_C_SOURCE=200809L)
endforeach()

install(TARGETS rafaelia_static rafaelia_shared
        ARCHIVE DESTINATION lib
        LIBRARY DESTINATION lib)
install(FILES raf_core_api.h DESTINATION include)
