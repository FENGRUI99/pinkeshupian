
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    public void startServer(){
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(10008);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ExecutorService pool = Executors.newCachedThreadPool();
        while (true){
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e){
                e.printStackTrace();
            }
            System.out.println("Client IP: " + clientSocket.getInetAddress() + ": " + clientSocket.getPort());
            pool.execute(new Sender(clientSocket));
        }
    }

    public static void main(String[] args) throws IOException{
        Server server = new Server();
        server.startServer();
    }
}

class Sender extends Thread{
    private Socket clientSocket;
    private DataOutputStream sOut = null;
    private DataInputStream in = null;
    private BufferedReader sIn = null;
    private File f = null;
    final String CRLF = "\r\n";
    public Sender(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }
    @Override
    public void run() {
            try {
                sOut = new DataOutputStream(clientSocket.getOutputStream());
                sIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                HashMap<String, String> request = new HashMap<>();
                String inputLine;
                String[] info;
                while ((inputLine = sIn.readLine()) != null) {
                    info = inputLine.split(" ");
                    while (!(inputLine = sIn.readLine()).equals("")) {
                        String[] tmp = inputLine.split(": ");
                        request.put(tmp[0], tmp[1]);
                    }
                    System.out.println(info[1]);
                    f = findFile(info[1]);
                    if (f.exists() && !request.containsKey("Range")){
                        response200();
                        System.out.println("response code: 200");
                    }
                    else if (f.exists()){
                        response206("", "");
                        System.out.println("response code: 206");
                    }
                    else {
                        response404();
                        System.out.println("response code: 404");
                    }
                }
                sOut.close();
                in.close();
                sIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

    }
    private File findFile(String path) {
        File file = new File("." + path);
        return file;
    }
    private void response200() throws IOException {
        //header
        String fType = URLConnection.guessContentTypeFromName(f.getName());
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEEE, dd ");
        SimpleDateFormat dateFormat3 = new SimpleDateFormat(" yyyy hh:mm:ss");
        LocalDate localDate = LocalDate.now();
        Month dateFormat2 = localDate.getMonth();
        String header = "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + f.length() + CRLF +
                "Content-Type: " + fType + CRLF +
                "Connection: " + "Keep-Alive" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Date: " + dateFormat1.format(date) + dateFormat2.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + dateFormat3.format(date) + " GMT" + CRLF +
                "Last-Modified: " + f.lastModified() + CRLF + CRLF;
       try {
            FileInputStream fis = new FileInputStream(f);
            in = new DataInputStream(fis);
            int max = 1024 * 1024 * 10;
            byte[] bytes = new byte[1024 * 1024];
            int length;
            sOut.writeUTF(header);
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {
                sOut.write(bytes, 0, length);
                sOut.flush();
                max -= length;
                if (max <= 0) break;
            }
            System.out.println(max);
            System.out.println("successful");
       } catch (Exception e) {
            e.printStackTrace();
       }

    }
    private void response206(String head, String tail) throws IOException{
        long startByte = Long.parseLong(head);
        long endByte;
        if (tail.equals("")){
            endByte = this.f.length();
        }
        else{
            endByte = Long.parseLong(tail);
        }
        String fType = URLConnection.guessContentTypeFromName(this.f.getName());
        Date date = new Date();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("EEEE, dd ");
        SimpleDateFormat dateFormat3 = new SimpleDateFormat(" yyyy hh:mm:ss");
        LocalDate localDate = LocalDate.now();
        Month dateFormat2 = localDate.getMonth();

        //form and send header
        String header = "HTTP/1.1 206 Partial Content" + CRLF +
                "Content-Length: " + (endByte - startByte + 1) + CRLF +
                "Content-Type: " + fType + CRLF +
                "Connection: " + "Keep-Alive" + CRLF +
                "Accept-Ranges: " + "bytes" + CRLF +
                "Content-Range: " + "bytes " + startByte + "-" + endByte + "/" + this.f.length() +  CRLF +
                "Date: " + dateFormat1.format(date) + dateFormat2.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + dateFormat3.format(date) + " GMT" + CRLF +
                "Last-Modified: " + this.f.lastModified() + CRLF + CRLF;
        sOut.writeUTF(header);
        //form and send file
        try {
            FileInputStream fis = new FileInputStream(this.f);
            in = new DataInputStream(fis);
            byte[] bytes = new byte[1024 * 1024]; //1MB

            int length;
            long count = 0;  //total bytes that read

            in.skip(startByte);  //Skip 'startbytes' bytes tp reach the start point
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {   //[)
                count += length;
                if(count <= endByte-startByte+1){
                    sOut.write(bytes, 0, length);
                    sOut.flush();
                }
                else{
                    if(count-length == endByte-startByte+1) break;
                    sOut.write(bytes, 0, (length-(int)(count-(endByte-startByte+1))));
                    sOut.flush();
                    break;
                }
            }
            System.out.println("successful");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void response404() throws IOException{
        String header = "HTTP/1.1 404 Not Found"  + CRLF + CRLF;
        sOut.writeUTF(header);
        sOut.flush();
    }
}

