package protocols;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Integer.valueOf;

public class SR {
    private final InetAddress host;
    private final int targetPort;
    private final int sourcePort;
    private final int WindowSize = 16;
    private long base = 0;
    int Loss = 5;

    public SR(String host, int targetPort, int sourcePort) throws UnknownHostException {
        this.sourcePort = sourcePort;
        this.host = InetAddress.getByName(host);
        this.targetPort = targetPort;
    }

    public void send(byte[] content) throws IOException {
        int sendIndex = 0, length;
        final int MAX_LENGTH = 1024;
        DatagramSocket datagramSocket = new DatagramSocket(sourcePort);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();
        List<Integer> timers = new LinkedList<>();
        long nextseqNum = base;
        do {
            while (timers.size() < WindowSize && sendIndex < content.length && nextseqNum < 256) {
                timers.add(0);
                // 缓存当前数据
                datagramBuffer.add(new ByteArrayOutputStream());
                // 数据帧长度
                length = Math.min(content.length - sendIndex, MAX_LENGTH);
                ByteArrayOutputStream oneSend = new ByteArrayOutputStream();
                //写入数据包
                byte[] temp = new byte[1];
                temp[0] = valueOf((int) base).byteValue();
                oneSend.write(temp, 0, 1);
                temp = new byte[1];
                temp[0] = valueOf((int) nextseqNum).byteValue();
                oneSend.write(temp, 0, 1);
                oneSend.write(content, sendIndex, length);

                DatagramPacket datagramPacket = new DatagramPacket(oneSend.toByteArray(), oneSend.size(), host, targetPort);
                datagramSocket.send(datagramPacket);
                // 数据包写入缓存
                datagramBuffer.get((int) (nextseqNum - base)).write(content, sendIndex, length);
                // 文件内容指针更新
                sendIndex += length;
                System.out.println("send the datagram : base " + base + " seq " + nextseqNum);
                nextseqNum++;
            }
            datagramSocket.setSoTimeout(1000);
            DatagramPacket receivePacket;
            try {
                // 窗口中由分组未确认
                while (!checkWindow(timers)) {
                    byte[] recv = new byte[1500];
                    receivePacket = new DatagramPacket(recv, recv.length);
                    datagramSocket.receive(receivePacket);
                    // 得到分组号
                    int ack = (int) ((recv[0] & 0x0FF) - base);
                    // 将对应分组设置为 -1
                    timers.set(ack, -1);
                }
            } catch (SocketTimeoutException e) {
                for (int i = 0; i < timers.size(); i++) {
                    if (timers.get(i) != -1)
                        // 计时
                        timers.set(i, timers.get(i) + 1);
                }
            }
            for (int i = 0; i < timers.size(); i++) { // update timer
                int sendMaxTime = 2;
                // 计时器超过，则重新发送
                if (timers.get(i) > sendMaxTime) {
                    ByteArrayOutputStream resender = new ByteArrayOutputStream();

                    byte[] temp = new byte[1];
                    temp[0] = valueOf((int) base).byteValue();
                    resender.write(temp, 0, 1);
                    temp = new byte[1];
                    temp[0] = valueOf((int) (i + base)).byteValue();
                    resender.write(temp, 0, 1);
                    resender.write(datagramBuffer.get(i).toByteArray(), 0, datagramBuffer.get(i).size());

                    DatagramPacket datagramPacket = new DatagramPacket(resender.toByteArray(), resender.size(), host, targetPort);
                    datagramSocket.send(datagramPacket);
                    System.err.println("resend the datagram : base " + base + " seq " + (i + base));
                    timers.set(i, 0);
                }
            }
            // 当前面都被收到，则滑动窗口
            int i = 0, s = timers.size();
            while (i < s) {
                if (timers.get(i) == -1) {
                    timers.remove(i);
                    datagramBuffer.remove(i);
                    base++;
                    s--;
                } else {
                    break;
                }
            }
            if (base >= 256) {
                base = base - 256;
                nextseqNum = nextseqNum - 256;
            }
        } while (sendIndex < content.length || timers.size() != 0);
        datagramSocket.close();
    }


    public ByteArrayOutputStream receive() throws IOException {
        // 接收数据帧个数
        int count = 0;
        int time = 0;
        long maxSeqNum = 0;
        long receiveBase = -1;
        // 保存交付给上层的数据
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DatagramSocket datagramSocket = new DatagramSocket(sourcePort);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();
        DatagramPacket receivePacket;
        datagramSocket.setSoTimeout(1000);
        // 初始化窗口缓冲区
        for (int i = 0; i < WindowSize; i++) {
            datagramBuffer.add(new ByteArrayOutputStream());
        }
        while (true) {
            try {
                byte[] recv = new byte[1500];
                receivePacket = new DatagramPacket(recv, recv.length, host, targetPort);
                datagramSocket.receive(receivePacket);

                if (count % Loss != 0) {
                    long base = recv[0] & 0x0FF;
                    long seq = recv[1] & 0x0FF;
                    if (receiveBase == -1)
                        receiveBase = base;

                    if (base != receiveBase) {
                        ByteArrayOutputStream temp = getBytes(datagramBuffer, (base - receiveBase) > 0 ? (base - receiveBase) : maxSeqNum + 1);
                        for (int i = 0; i < base - receiveBase; i++) {
                            datagramBuffer.remove(0);
                            datagramBuffer.add(new ByteArrayOutputStream());
                        }
                        result.write(temp.toByteArray(), 0, temp.size());
                        maxSeqNum -= base - receiveBase;
                        receiveBase = base;
                    }
                    if (seq - base > maxSeqNum) {
                        maxSeqNum = seq - base;
                    }
                    ByteArrayOutputStream recvBytes = new ByteArrayOutputStream();
                    recvBytes.write(recv, 2, receivePacket.getLength() - 2);
                    datagramBuffer.set((int) (seq - base), recvBytes);
                    // send ACK
                    recv = new byte[1];
                    recv[0] = valueOf((int) seq).byteValue();
                    receivePacket = new DatagramPacket(recv, recv.length, host, targetPort);
                    datagramSocket.send(receivePacket);
                    System.out.println("receive datagram : base " + base + " seq " + seq);
                }
                count++;
                time = 0;
            } catch (IOException e) {
                time++;
            }
            // max time for one datagram
            int receiveMaxTime = 4;
            if (time > receiveMaxTime) {  // check if the connect out of time
                ByteArrayOutputStream temp = getBytes(datagramBuffer, maxSeqNum + 1);
                result.write(temp.toByteArray(), 0, temp.size());
                break;
            }
        }
        datagramSocket.close();
        return result;
    }

    private ByteArrayOutputStream getBytes(List<ByteArrayOutputStream> buffer, long max) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (int i = 0; i < max; i++) {
            if (buffer.get(i) != null)
                result.write(buffer.get(i).toByteArray(), 0, buffer.get(i).size());
        }
        return result;
    }

    private boolean checkWindow(List<Integer> timers) {
        for (Integer timer : timers) {
            if (timer != -1)
                return false;
        }
        return true;
    }
}
