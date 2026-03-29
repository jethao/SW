cmake_minimum_required(VERSION 3.20)

project(airhealth_fw_source LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(airhealth_fw_pairing
  src/pairing.cpp
  src/session.cpp
  src/session_contract.cpp
  src/oral.cpp
  src/low_power.cpp
  src/fat.cpp
  src/session_journal.cpp
  src/led_button.cpp
  src/routing.cpp
  src/factory_auth.cpp
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

add_executable(session_contract_test
  tests/session_contract_test.cpp
)

target_link_libraries(session_contract_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(oral_test
  tests/oral_test.cpp
)

target_link_libraries(oral_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(low_power_test
  tests/low_power_test.cpp
)

target_link_libraries(low_power_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(fat_test
  tests/fat_test.cpp
)

target_link_libraries(fat_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(led_button_test
  tests/led_button_test.cpp
)

target_link_libraries(led_button_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(session_journal_test
  tests/session_journal_test.cpp
)

target_link_libraries(session_journal_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(routing_test
  tests/routing_test.cpp
)

target_link_libraries(routing_test
  PRIVATE
    airhealth_fw_pairing
)

add_executable(factory_auth_test
  tests/factory_auth_test.cpp
)

target_link_libraries(factory_auth_test
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

add_test(
  NAME session_contract_test
  COMMAND session_contract_test
)

add_test(
  NAME oral_test
  COMMAND oral_test
)

add_test(
  NAME low_power_test
  COMMAND low_power_test
)

add_test(
  NAME fat_test
  COMMAND fat_test
)

add_test(
  NAME led_button_test
  COMMAND led_button_test
)

add_test(
  NAME session_journal_test
  COMMAND session_journal_test
)

add_test(
  NAME routing_test
  COMMAND routing_test
)

add_test(
  NAME factory_auth_test
  COMMAND factory_auth_test
)
