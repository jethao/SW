cmake_minimum_required(VERSION 3.20)

project(airhealth_fw_source LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(airhealth_fw_pairing
  src/pairing.cpp
  src/session.cpp
)

target_include_directories(airhealth_fw_pairing
  PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_executable(pairing_test
  tests/pairing_test.cpp
)

target_link_libraries(pairing_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(session_test
  tests/session_test.cpp
)

target_link_libraries(session_test
  PRIVATE
    airhealth_fw_pairing
)

enable_testing()

add_test(
  NAME pairing_test
  COMMAND pairing_test
)

add_test(
  NAME session_test
  COMMAND session_test
)
