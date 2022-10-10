import protocols.SR;

import java.io.*;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        SR client = new SR("localhost", 7070, 8080);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        File txt4Client = new File("./src/txt4Client");
        FileInputStream fileInputStream = new FileInputStream(txt4Client);
        // 将文件读入输出流
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fileInputStream.read(buffer)) > 0) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        byteArrayOutputStream.flush();

        System.out.println("Start to send txt4Client to " + "localhost " + 7070);
        client.send(byteArrayOutputStream.toByteArray());

        System.out.println("\nStart to receive txt4Server from " + "localhost " + 7070);

        File clientRecv = new File("./src/clientRecv");
        if(clientRecv.createNewFile()) {
            System.out.println("\nStart to receive txt4Server from " + "localhost " + 7070);
        }
        while (true) {
            byteArrayOutputStream = client.receive();
            if (byteArrayOutputStream.size() != 0) {
                FileOutputStream fileOutputStream = new FileOutputStream(clientRecv);
                fileOutputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
                fileOutputStream.close();
                System.out.println("Get the file ");
                System.out.println("Saved as clientRecv");
                break;
            }
        }
    }
}
