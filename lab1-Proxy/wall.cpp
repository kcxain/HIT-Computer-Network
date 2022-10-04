//
// Created by 10670 on 2022/10/4.
//

#include "wall.h"
// 钓鱼网站转换表
map<string, string> phish =
        {
                {"www.hit.edu.cn", "www.hljp.edu.cn" },
        };
// 禁止访问网站
set<string> ban = {
        "jwts.hit.edu.cn"
};


void ban_header(HttpHeader *httpHeader, char *buffer) {
    //没找到，返回
    if(!ban.count(string(httpHeader->host))) {
        return;
    }
    // 将其设置为无效
    httpHeader->valid = 1;
}
void transfer_header(HttpHeader *httpHeader, char *buffer) {
    if(!phish.count(string(httpHeader->host))) {
        return;
    }
    string old_host = string(httpHeader->host);
    string new_host = phish[old_host];
    string buf = string(buffer);
    //修改报文
    while(buf.find(old_host) != string::npos)
    {
        int l = buf.find(old_host);
        buf = buf.substr(0,l) + new_host + buf.substr(l+old_host.length());
    }
    //修改header
    memcpy(httpHeader->host, new_host.c_str(), new_host.length() + 1);
    memcpy(buffer, buf.c_str(), buf.size() + 1);
}