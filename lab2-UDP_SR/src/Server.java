import protocols.SR;

import java.io.*;

public class Server {
    public static void main(String[] args) throws IOException, InterruptedException {
        SR server = new SR("localhost", 8080, 7070);

        // 服务器先接收文件
        File serverRecv = new File("./src/serverRecv");
        if(serverRecv.createNewFile()) {
            System.out.println("Start to receive file 1.png from " + "localhost " + 8080);
        }
        while (true) {
            ByteArrayOutputStream byteArrayOutputStream = server.receive();
            if (byteArrayOutputStream.size() != 0) {
                FileOutputStream fileOutputStream = new FileOutputStream(serverRecv);
                fileOutputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
                fileOutputStream.close();
                System.out.println("Get the file ");
                System.out.println("Saved as serverRecv");
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