//
// Created by 10670 on 2022/10/3.
//

#ifndef LAB1_PROXY_TYPE_H
#define LAB1_PROXY_TYPE_H
#include <cstdio>
#include <Windows.h>
#include <process.h>
#include <cstring>
#include <vector>
#include <string>
#define MAXSIZE 102400       //发送数据报文的最大长度
#define MAX_FILE_NAME 100   //最大缓存文件长度
#define DATE_SIZE 50        //日期长度
#define HTTP_PORT 80        //http 服务器端口


//Http 重要头部数据
struct HttpHeader{
    char method[4]{}; // POST 或者 GET，注意有些为 CONNECT，本实验暂不考虑
    char url[1024]{}; // 请求的 url
    char host[1024]{}; // 目标主机
    char cookie[1024 * 10]{};   //cookie
    int valid{};               //是否有效
    HttpHeader(){
        memset(this,0,sizeof(HttpHeader));
    }
};
struct ProxyParam{
    SOCKET clientSocket;
    SOCKET serverSocket;
};
#endif //LAB1_PROXY_TYPE_H
