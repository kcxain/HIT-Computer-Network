cmake_minimum_required(VERSION 3.23)
project(lab1_Proxy)

set(CMAKE_CXX_STANDARD 14)
set(SOURCES wall.cpp Cache.cpp main.cpp)
add_executable(lab1_Proxy ${SOURCES})
target_link_libraries(lab1_Proxy -lwsock32)

