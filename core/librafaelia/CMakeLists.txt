cmake_minimum_required(VERSION 3.16)
project(rafaelia_core C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

add_library(rafaelia_static STATIC raf_cpu_parser.c)
add_library(rafaelia_shared SHARED raf_cpu_parser.c)

set_target_properties(rafaelia_static rafaelia_shared PROPERTIES OUTPUT_NAME rafaelia)

set(RAFAELIA_WARNINGS -Wall -Wextra -Werror)
set(RAFAELIA_PUBLIC_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../include)

foreach(target rafaelia_static rafaelia_shared)
  target_include_directories(${target}
    PUBLIC
      $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
      $<BUILD_INTERFACE:${RAFAELIA_PUBLIC_INCLUDE_DIR}>
      $<INSTALL_INTERFACE:include>)
  target_compile_options(${target} PRIVATE ${RAFAELIA_WARNINGS})
  target_compile_definitions(${target} PRIVATE _POSIX_C_SOURCE=200809L)
endforeach()

install(TARGETS rafaelia_static rafaelia_shared
        ARCHIVE DESTINATION lib
        LIBRARY DESTINATION lib)
install(FILES ${RAFAELIA_PUBLIC_INCLUDE_DIR}/raf_core.h DESTINATION include)
