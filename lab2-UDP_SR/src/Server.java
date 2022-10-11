import protocols.SR;

import java.io.*;
import java.util.Scanner;

public class Server {
    public static void main(String[] args) throws IOException, InterruptedException {
        SR server = new SR("localhost", 15213, 15000);
        System.out.println("服务器启动成功，端口号：" + 15000);


        
        // 服务器先接收文件
        Scanner scan = new Scanner(System.in);
        String fileName = scan.nextLine();

        File serverRecv = new File("./src/" + fileName);
        if(serverRecv.createNewFile()) {
            System.out.println("开始接收文件： " + fileName);
        }
        while (true) {
            ByteArrayOutputStream byteArrayOutputStream = server.receive();
            if (byteArrayOutputStream.size() != 0) {
                FileOutputStream fileOutputStream = new FileOutputStream(serverRecv);
                fileOutputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
                fileOutputStream.close();
                System.out.println("文件接收完毕");
                fileOutputStream.close();
                break;
            }
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        File txt4Server = new File("./src/txt4Server");
        FileInputStream fileInputStream = new FileInputStream(txt4Server);
        // 将文件读入输出流
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fileInputStream.read(buffer)) > 0) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        byteArrayOutputStream.flush();
        System.out.println("\nStart to send file txt4Server to " + "localhost" + 8080);
        server.send(byteArrayOutputStream.toByteArray());
    }
}