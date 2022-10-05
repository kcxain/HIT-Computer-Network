//
// Created by 10670 on 2022/10/4.
//

#ifndef LAB1_PROXY_WALL_H
#define LAB1_PROXY_WALL_H
#include "type.h"
#include<map>
#include<set>
using namespace std;

void ban_header(HttpHeader *httpHeader, char *buffer);          // 拒绝访问
void transfer_header(HttpHeader *httpHeader, char *buffer);     // 钓鱼

#endif //LAB1_PROXY_WALL_H
