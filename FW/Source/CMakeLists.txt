cmake_minimum_required(VERSION 3.20)

project(airhealth_fw_source LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(airhealth_fw_device_info
  src/device_info.cpp
)

target_include_directories(airhealth_fw_device_info
  PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_executable(device_info_test
  tests/device_info_test.cpp
)

target_link_libraries(device_info_test
  PRIVATE
    airhealth_fw_device_info
)

enable_testing()

add_test(
  NAME device_info_test
  COMMAND device_info_test
)
