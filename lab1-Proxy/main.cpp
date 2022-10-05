#include "Cache.h"
#include "type.h"
#include "wall.h"
#pragma comment(lib,"Ws2_32.lib")


BOOL InitSocket();
BOOL ParseHttpHead(char *Cachebuffer, HttpHeader *httpHeader, char *buffer);
BOOL ConnectToServer(SOCKET *serverSocket,char *host);
unsigned int __stdcall ProxyThread(LPVOID lpParameter);


//代理相关参数
SOCKET ProxyServer;
sockaddr_in ProxyServerAddr;
const int ProxyPort = 10240;


int main()
{
    printf("代理服务器正在启动\n");
    printf("初始化...\n");
    if(!InitSocket()){
        printf("socket 初始化失败\n");
        return -1;
    }
    printf("代理服务器正在运行，监听端口 %d\n",ProxyPort);
    SOCKET acceptSocket = INVALID_SOCKET;
    ProxyParam *lpProxyParam = NULL;
    HANDLE hThread;

    //代理服务器不断监听
    while(true){
        acceptSocket = accept(ProxyServer,NULL,NULL);
        lpProxyParam = new ProxyParam;
        if(lpProxyParam==NULL) {
            continue;
        }
        lpProxyParam->clientSocket = acceptSocket;
        // 开启线程
        hThread = (HANDLE)_beginthreadex(NULL, 0,
                                         &ProxyThread,(LPVOID)lpProxyParam, 0, 0);
        CloseHandle(hThread);
        Sleep(200);
    }
    closesocket(ProxyServer);
    WSACleanup();
    return 0;
}
//************************************
// Method: InitSocket
// FullName: InitSocket
// Access: public
// Returns: BOOL
// Qualifier: 初始化套接字
//************************************
BOOL InitSocket(){
    //加载套接字库（必须）
    WORD wVersionRequested;
    WSADATA wsaData;
    //套接字加载时错误提示
    int err;
    //版本 2.2
    wVersionRequested = MAKEWORD(2, 2);
    //加载 dll 文件 Scoket 库
    err = WSAStartup(wVersionRequested, &wsaData);
    if(err != 0){
        //找不到 winsock.dll
        printf("加载 winsock 失败，错误代码为: %d\n", WSAGetLastError());
        return FALSE;
    }
    if(LOBYTE(wsaData.wVersion) != 2 || HIBYTE(wsaData.wVersion) !=2)
    {
        printf("不能找到正确的 winsock 版本\n");
        WSACleanup();
        return FALSE;
    }
    // TCP 连接
    ProxyServer= socket(AF_INET, SOCK_STREAM, 0);
    if(INVALID_SOCKET == ProxyServer){
        printf("创建套接字失败，错误代码为：%d\n",WSAGetLastError());
        return FALSE;
    }
    ProxyServerAddr.sin_family = AF_INET;
    ProxyServerAddr.sin_port = htons(ProxyPort);
    // 设置IP, 过滤用户
    ProxyServerAddr.sin_addr.S_un.S_addr = inet_addr("127.0.0.5");
    //ProxyServerAddr.sin_addr.S_un.S_addr = inet_addr("127.0.0.1");
    //ProxyServerAddr.sin_addr.S_un.S_addr = INADDR_ANY;
    //绑定套接字
    if(bind(ProxyServer,(SOCKADDR*)&ProxyServerAddr,sizeof(SOCKADDR)) == SOCKET_ERROR){
        printf("绑定套接字失败\n");
        return FALSE;
    }

    if(listen(ProxyServer, SOMAXCONN) == SOCKET_ERROR){
        printf("监听端口%d 失败",ProxyPort);
        return FALSE;
    }
    return TRUE;
}
//************************************
// Method: ProxyThread
// FullName: ProxyThread
// Access: public
// Returns: unsigned int __stdcall
// Qualifier: 线程执行函数
// Parameter: LPVOID lpParameter

//************************************
unsigned int __stdcall ProxyThread(LPVOID lpParameter){
    char Buffer[MAXSIZE];
    char *CacheBuffer;
    memset(Buffer, 0, MAXSIZE);

    int recvSize;
    int ret = 1;
    bool flag = false;
    // 从客户端接收
    recvSize = recv(((ProxyParam*)lpParameter)->clientSocket,Buffer,MAXSIZE,0);
    if(recvSize <= 0) {
        goto error;
    }

    HttpHeader *httpHeader;
    httpHeader = new HttpHeader();
    CacheBuffer = new char[recvSize + 1];
    // 复制一套报文
    memset(CacheBuffer, 0, recvSize + 1);
    memcpy(CacheBuffer,Buffer,recvSize);

    char filename[MAX_FILE_NAME];
    ZeroMemory(filename, MAX_FILE_NAME);

    // 解析HTTP头
    ParseHttpHead(CacheBuffer, httpHeader,Buffer);
    // 判断host是否有效
    if(httpHeader->valid == 1) {
        printf("该网站已被屏蔽: %s \n", httpHeader->host);
        goto error;
    }

    // 查询是否在缓存中
    flag = query_Cache(Buffer, httpHeader, filename);
    // phish(httpHeader);

    delete CacheBuffer;
    // 连接服务器
    if(!ConnectToServer(&((ProxyParam*)lpParameter)->serverSocket,httpHeader->host)) {
        goto error;
    }
    printf("代理连接主机 %s 成功\n",httpHeader->host);
    //将客户端发送的 HTTP 数据报文直接转发给目标服务器
    ret = send(((ProxyParam *)lpParameter)->serverSocket,Buffer,strlen(Buffer)+ 1,0);
    if(ret < 0) {
        goto error;
    }
    //等待目标服务器返回数据
    recvSize = recv(((ProxyParam*)lpParameter)->serverSocket,Buffer,MAXSIZE,0);
    if(recvSize <= 0){
        goto error;
    }
    //在缓存中
    if(flag == TRUE) {
        char *p, num[10], tempBuffer[MAXSIZE + 1];
        const char * delim = "\r\n";
        ZeroMemory(num, 10);
        ZeroMemory(tempBuffer, MAXSIZE + 1);
        memcpy(tempBuffer, Buffer, strlen(Buffer));
        p = strtok(tempBuffer, delim);
        memcpy(num, &p[9], 3);
        // 返回如果是304，则直接读缓存写入
        if (strcmp(num, "304") == 0)
            read_Cache(filename, Buffer);
        else
            // 否则，重新写缓存
            write_Cache(httpHeader->url, Buffer);
    }
    else {
        write_Cache(httpHeader->url, Buffer);
    }

    //将目标服务器返回的数据直接转发给客户端
    send(((ProxyParam*)lpParameter)->clientSocket,Buffer,sizeof(Buffer),0);
    //错误处理
    error:
    printf("关闭套接字\n");
    Sleep(500);
    closesocket(((ProxyParam*)lpParameter)->clientSocket);
    closesocket(((ProxyParam*)lpParameter)->serverSocket);
    free(lpParameter);
    _endthreadex(0);
}
//************************************
// Method: ParseHttpHead
// FullName: ParseHttpHead
// Access: public
// Returns: void
// Qualifier: 解析 TCP 报文中的 HTTP 头部
// Parameter: char * buffer
// Parameter: HttpHeader * httpHeader
//************************************
BOOL ParseHttpHead(char *Cachebuffer, HttpHeader * httpHeader, char *buffer){
    char *p;
    char *ptr;
    const char * delim = "\r\n";
    p = strtok_r(Cachebuffer,delim,&ptr);//提取第一行
    printf("%s\n",p);
    if(p[0] == 'G'){//GET 方式
        memcpy(httpHeader->method,"GET",3);
        memcpy(httpHeader->url,&p[4],strlen(p) -13);
    }else if(p[0] == 'P'){//POST 方式
        memcpy(httpHeader->method,"POST",4);
        memcpy(httpHeader->url,&p[5],strlen(p) - 14);
    }

    printf("%s\n",httpHeader->url);
    p = strtok_r(NULL,delim,&ptr);
    while(p){
        switch(p[0]){
            case 'H'://Host
                memcpy(httpHeader->host,&p[6],strlen(p) - 6);
                break;
            case 'C'://Cookie
                if(strlen(p) > 8){
                    char header[8];
                    memset(header, 0, sizeof(header));
                    memcpy(header, p, 6);
                    if(!strcmp(header,"Cookie")){
                        memcpy(httpHeader->cookie,&p[8],strlen(p) -8);
                    }
                }
                break;
            default:
                break;
        }
        p = strtok_r(NULL,delim,&ptr);
    }
    //判断是否禁止访问
    ban_header(httpHeader, buffer);
    //判断是否要钓鱼
    transfer_header(httpHeader, buffer);
    return TRUE;
}
//************************************
// Method: ConnectToServer
// FullName: ConnectToServer
// Access: public
// Returns: BOOL
// Qualifier: 根据主机创建目标服务器套接字，并连接
// Parameter: SOCKET * serverSocket
// Parameter: char * host
//************************************
BOOL ConnectToServer(SOCKET *serverSocket,char *host){
    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(HTTP_PORT);
    HOSTENT *hostent = gethostbyname(host);
    if(!hostent){
        return FALSE;
    }
    in_addr Inaddr=*((in_addr*) *hostent->h_addr_list);
    serverAddr.sin_addr.s_addr = inet_addr(inet_ntoa(Inaddr));
    *serverSocket = socket(AF_INET,SOCK_STREAM,0);
    if(*serverSocket == INVALID_SOCKET){
        return FALSE;
    }
    if(connect(*serverSocket,(SOCKADDR *)&serverAddr,sizeof(serverAddr))== SOCKET_ERROR){
        closesocket(*serverSocket);
        return FALSE;
    }
    return TRUE;
}
