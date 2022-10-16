package protocols;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Integer.valueOf;

public class SR {
    // 模拟丢包，每packetLoss次丢包一次
    final int packetLoss = 6;
    // 主机名
    private final InetAddress host;
    // 目标端口
    private final int targetPort;
    // 本机端口
    private final int sourcePort;
    // 窗口大小
    private final int windowSize = 8;
    // 序号空间大小
    final int MAX_SEQ = 256;
    // 文件最大大小
    final int MAX_LENGTH = 1024;
    private long base = 0;

    public SR(String host, int targetPort, int sourcePort) throws UnknownHostException {
        this.sourcePort = sourcePort;
        this.host = InetAddress.getByName(host);
        this.targetPort = targetPort;
    }


    public void send(byte[] content) throws IOException {
        DatagramSocket datagramSocket = new DatagramSocket(sourcePort);
        // 内容缓冲区，保存分组
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();
        // 计时器，为每一个分组计时
        List<Integer> timers = new LinkedList<>();
        int sendIndex = 0;

        // 初始时，nextseqNum = base
        long nextseqNum = base;
        do {
            while (timers.size() < windowSize && sendIndex < content.length && nextseqNum < MAX_SEQ) {
                // 缓存当前数据
                datagramBuffer.add(new ByteArrayOutputStream());
                // 当前数据计时器置 0
                timers.add(0);
                // 数据帧长度
                int length = Math.min(content.length - sendIndex, MAX_LENGTH);
                ByteArrayOutputStream oneSend = new ByteArrayOutputStream();
                //写入数据包 base + nextseqNum + content
                writeByte(oneSend, (int)base);
                writeByte(oneSend, (int)nextseqNum);
                oneSend.write(content, sendIndex, length);

                DatagramPacket datagramPacket = new DatagramPacket(oneSend.toByteArray(), oneSend.size(), host, targetPort);
                datagramSocket.send(datagramPacket);
                // 数据包写入缓存
                datagramBuffer.get((int) (nextseqNum - base)).write(content, sendIndex, length);
                // 文件内容指针更新
                sendIndex += length;
                System.out.println("发送分组" + nextseqNum + " base" + base);
                // 序号++
                nextseqNum++;
            }
            // 设置延时等待
            datagramSocket.setSoTimeout(500);
            DatagramPacket receivePacket;
            try {
                // 窗口中由分组未确认，如果确认则设置为-1
                while (!checkWindow(timers)) {
                    byte[] recv = new byte[1500];
                    receivePacket = new DatagramPacket(recv, recv.length);
                    datagramSocket.receive(receivePacket);
                    // 得到分组号
                    int oAck = (int)(recv[0]);
                    int ack = (int) ((recv[0]) - base);
                    System.out.println("收到ACK" + oAck);
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
            for (int i = 0; i < timers.size(); i++) {
                int sendMaxTime = 2;
                // 计时器超过，则重新发送
                if (timers.get(i) > sendMaxTime) {
                    ByteArrayOutputStream resender = new ByteArrayOutputStream();
                    // 从缓存中发送
                    writeByte(resender, (int)(base));
                    writeByte(resender, (int)(i + base));
                    resender.write(datagramBuffer.get(i).toByteArray(), 0, datagramBuffer.get(i).size());
                    DatagramPacket datagramPacket = new DatagramPacket(resender.toByteArray(), resender.size(), host, targetPort);
                    datagramSocket.send(datagramPacket);
                    System.err.println("超时，重发分组" + (i + base));
                    timers.set(i, 0);
                }
            }
            // 当前面有收到的，则滑动窗口
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
            // 对序号空间求余
            base %= MAX_SEQ;
            nextseqNum %= MAX_SEQ;
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
        datagramSocket.setSoTimeout(500);
        // 初始化窗口缓冲区
        for (int i = 0; i < windowSize; i++) {
            datagramBuffer.add(new ByteArrayOutputStream());
        }

        while (true) {
            try {
                // 接收数据
                byte[] recv = new byte[MAX_LENGTH];
                receivePacket = new DatagramPacket(recv, recv.length, host, targetPort);
                datagramSocket.receive(receivePacket);
                // 模拟丢包，当整除时不处理
                if (count % packetLoss != 0) {
                    long base = recv[0];
                    long seq = recv[1];
                    // 先让接收窗口base置为发送窗口
                    if (receiveBase == -1)
                        receiveBase = base;
                    if (base != receiveBase) {
                        ByteArrayOutputStream temp = getBytes(datagramBuffer, (base - receiveBase) > 0 ? (base - receiveBase) : maxSeqNum + 1);
                        for (int i = 0; i < base - receiveBase; i++) {
                            datagramBuffer.remove(0);
                            datagramBuffer.add(new ByteArrayOutputStream());
                        }
                        // 如果不等，交付
                        result.write(temp.toByteArray(), 0, temp.size());
                        maxSeqNum -= base - receiveBase;
                        receiveBase = base;
                    }
                    if (seq - base > maxSeqNum) {
                        maxSeqNum = seq - base;
                    }
                    // 写入数据
                    ByteArrayOutputStream recvBytes = new ByteArrayOutputStream();
                    recvBytes.write(recv, 2, receivePacket.getLength() - 2);
                    datagramBuffer.set((int) (seq - base), recvBytes);
                    // ACK 格式：seq
                    recv = new byte[1];
                    recv[0] = valueOf((int) seq).byteValue();
                    receivePacket = new DatagramPacket(recv, recv.length, host, targetPort);
                    datagramSocket.send(receivePacket);

                    System.out.println("收到分组" + seq + " " + "发送ACK" + seq + " base" + receiveBase);
                }
                count++;
                time = 0;
            } catch (IOException e) {
                time++;
            }
            int receiveMaxTime = 4;
            if (time > receiveMaxTime) {
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
    // 检查是否都设置为-1
    private boolean checkWindow(List<Integer> timers) {
        for (Integer timer : timers) {
            if (timer != -1)
                return false;
        }
        return true;
    }

    // 构造byte数组
    private void writeByte(ByteArrayOutputStream byteArrayOutputStream, int data) {
        byte[] temp = new byte[1];
        temp[0] = valueOf((int) data).byteValue();
        byteArrayOutputStream.write(temp, 0, 1);
    }
}
