cmake_minimum_required(VERSION 3.20.0)

find_package(Zephyr REQUIRED HINTS $ENV{ZEPHYR_BASE})
project(airhealth_fw_app LANGUAGES C CXX)

target_sources(app PRIVATE
  src/main.cpp
  src/device_info_service.cpp
  ../src/pairing.cpp
  ../src/led_button.cpp
)

target_include_directories(app PRIVATE
  ../include
  src
)

target_compile_features(app PRIVATE cxx_std_17)
