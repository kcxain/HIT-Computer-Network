//
// Created by 10670 on 2022/10/3.
//
#include "type.h"
#ifndef LAB1_PROXY_CACHE_H
#define LAB1_PROXY_CACHE_H
BOOL query_Cache(char* buf, HttpHeader* httpHeader, char* filename); // 查询是否在缓存中
void write_Cache(char *url, char *buf);                              // 写入缓存
void read_Cache(char *filename, char *buf);                          // 读缓存
void generate_File(char *url, char *filename);                       // 生成缓存标识
void Parse_Date(char *buffer, char *field, char *tempDate);          // 生成日期
void generate_HTTP(char *buffer, char *value);
#endif //LAB1_PROXY_CACHE_H
