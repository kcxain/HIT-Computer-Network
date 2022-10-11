import protocols.SR;

import java.io.*;
import java.util.Scanner;

public class Client {
    static SR client;
    public static void main(String[] args) throws IOException, InterruptedException {
        client = new SR("localhost", 15000, 15213);
        System.out.println("客户端启动成功，端口号：" + 15213);
        while(true) {

            Scanner scan = new Scanner(System.in);
            String method = scan.nextLine();
            String[] m = method.split(" ");
            if(m[0].equals("exit")) {
                break;
            }
            String fileName = m[1];
            if(m[0].equals("GET")) {
                getFile(fileName);
            }
            if(m[0].equals("POST")) {
                postFile(fileName);
            }
        }
    }
    public static void getFile(String fileName) throws IOException {
        File serverRecv = new File("./src/" + fileName);
        if(!serverRecv.exists()) {
            serverRecv.createNewFile();
        }
        System.out.println("开始接收文件： " + fileName);
        while (true) {
            ByteArrayOutputStream byteArrayOutputStream = client.receive();
            if (byteArrayOutputStream.size() != 0) {
                FileOutputStream fileOutputStream = new FileOutputStream(serverRecv);
                fileOutputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
                fileOutputStream.close();
                System.out.println("文件接收完毕");
                fileOutputStream.close();
                break;
            }
        }
    }
    public static void postFile(String fileName) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        File txt4Server = new File("./src/" + fileName);
        FileInputStream fileInputStream = new FileInputStream(txt4Server);
        // 将文件读入输出流
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fileInputStream.read(buffer)) > 0) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        byteArrayOutputStream.flush();
        System.out.println("开始传输文件");
        client.send(byteArrayOutputStream.toByteArray());
        System.out.println("文件传输完成");
    }
}
