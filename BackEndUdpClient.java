//import java.io.*;
import com.alibaba.fastjson.JSONObject;

import java.net.*;
import java.util.HashMap;


public class BackEndUdpClient {

    final int chunkSize = 1024;
    int start = 0;
    int length = 0;//input
    int windowSize = 2;
    public void startClient() throws Exception {
        InetAddress serverAdd = InetAddress.getByName("172.16.7.10");
        DatagramSocket dsocket = new DatagramSocket( );
        getFileInfo("test.png", serverAdd, dsocket);



        int fileSize = 0;
        int recePointer = 0;

        int receSize = 0;
        HashMap<Integer, byte[]> store = new HashMap<>();
    L1:
        while(true){
            //get header and content
            byte[] receiveArr = new byte[9000];
            DatagramPacket dpacket = new DatagramPacket(receiveArr, receiveArr.length, serverAdd, 7077);
            dsocket.receive(dpacket);                                // receive the packet
            byte[] info = dpacket.getData();
            int headerLen = convertByteToInt(info, 0);
            int contentLen = convertByteToInt(info, 4);
            ResponseHeader header = JSONObject.parseObject(new String(info, 8, headerLen), ResponseHeader.class);
            //System.out.println(header.toString());
            byte[] content = new byte[contentLen];
            for(int i = 0; i < content.length; i++){ content[i] = info[8+headerLen+i]; }
            //judge header
            if(header.statusCode==0){
                fileSize = (int) header.length;
                length = fileSize-start;
                requestRange(header.fileName, serverAdd, dsocket, start, length);
            }
            else if(header.statusCode==1){
                store.put(header.sequence, content);
                while(store.containsKey(recePointer)){
                    recePointer++;
                    start += chunkSize;
                    length -= chunkSize;
                    if(length < 0){ //到达接受长度
                        close(header.fileName, serverAdd, dsocket);
                        break L1;
                    }
                    receSize++;
                }
                if(receSize == windowSize) {
                    requestRange(header.fileName, serverAdd, dsocket, start, length);
                    receSize = 0;
                }
                //windowSize++;


            }else{ //Not found
                break;
            }
            System.out.println("end this transmission.");
        }


    }

    public void getFileInfo(String fileName, InetAddress serverAdd, DatagramSocket dsocket) throws Exception{
        byte[] header = getReqHeader(0, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        System.out.println("Status_0 send success");
    }
    public void requestRange(String fileName, InetAddress serverAdd, DatagramSocket dsocket, long start, long length) throws Exception{
        byte[] header = getReqHeader(1, fileName, start, length).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        System.out.println("Status_1 send success");
    }
    public void close (String fileName, InetAddress serverAdd, DatagramSocket dsocket) throws Exception{
        byte[] header = getReqHeader(2, fileName, 0, 0).getBytes();
        byte[] preHeader = getPreHeader(header.length, 0);
        byte[] sendArr = addTwoBytes(preHeader, header);

        DatagramPacket dpacket = new DatagramPacket(sendArr, sendArr.length, serverAdd, 7077);
        dsocket.send(dpacket);
        dsocket.close();
        System.out.println("Status_2 send success");
    }

    public byte[] getPreHeader(int headerLen, int contentLen){
        byte[] headerLenBytes = convertIntToByte(headerLen);
        byte[] contentLenBytes = convertIntToByte(contentLen);
        byte[] res = addTwoBytes(headerLenBytes, contentLenBytes);
        return res;
    }
    public byte[] addTwoBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }
    public int convertByteToInt (byte[] bytes){
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8 ) |
                ((bytes[3] & 0xFF) << 0 );
    }
    public int convertByteToInt(byte[] bytes, int start){
        return ((bytes[start + 0] & 0xFF) << 24) |
                ((bytes[start + 1] & 0xFF) << 16) |
                ((bytes[start + 2] & 0xFF) << 8 ) |
                ((bytes[start + 3] & 0xFF) << 0 );
    }
    public byte[] convertIntToByte(int value){
        return new byte[] {
                (byte)(value >> 24),
                (byte)(value >> 16),
                (byte)(value >> 8),
                (byte)value };
    }
    public String getReqHeader (int statusCode, String fileName, long start, long length){
        RequestHeader r = new RequestHeader(statusCode, fileName, start, length);
        return JSONObject.toJSONString(r);
    }
    public String getResHeader (int statusCode, String fileName, long start, long length, int sequence, long lastModified, String md5){
        return JSONObject.toJSONString(new ResponseHeader(statusCode, fileName, start, length, sequence, lastModified, md5));
    }

    public static void main(String[] args) throws Exception{
        BackEndUdpClient client = new BackEndUdpClient();
        client.startClient();
    }
}
