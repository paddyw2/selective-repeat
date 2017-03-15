/**
 * FastClient Class
 * 
 * FastClient implements a basic reliable FTP client application based on UDP data transmission and selective repeat protocol
 * 
 */
import java.net.*;
import java.io.*;
import java.util.*;

public class FastClient {

    private Socket socket;
    private DatagramSocket UDPSocket;
    private InetAddress IPAddress;
    private String serverName;
    private int serverPort;
    private String fileName;
    private int responseTimeout;
    private DataOutputStream output;
    private DataInputStream input;   
    private int windowSize;
    private TxQueue window;
    private AckReceive ackReceiver;
    private Timer timer;

    /**
        * Constructor to initialize the program 
        * 
        * @param server_name    server name or IP
        * @param server_port    server port
        * @param file_name      file to be transfered
        * @param window         window size
        * @param timeout    time out value
        */
    public FastClient(String server_name, int server_port, int window, int timeout) {
        /* initialize */    
        /* Initialize values */
        serverName = server_name;
        serverPort = server_port;
        responseTimeout = timeout;
        windowSize = window;
        this.window = new TxQueue(window);
        timer = new Timer(true);

        // create sender socket
        try {
            UDPSocket = new DatagramSocket();
        } catch (Exception e) {
            System.out.println("UDP socket init failure");
            System.out.println(e.getMessage());
        }

        // create IP address
        IPAddress = null;
        try {
            IPAddress = InetAddress.getByName(serverName);
        } catch (Exception e) {
            System.out.println("Inet error");
            System.out.println(e.getMessage());
        }

        ackReceiver = new AckReceive(UDPSocket, this.window);
    }
    
    /* send file */

    public void send(String file_name) {
        // note file name
        fileName = file_name;
        // send handshake
        boolean handshakeSuccess = TCPHandshake();
        if(!handshakeSuccess) {
            System.out.println("Handshake failure - terminating");
            System.exit(1);
        }
        
        // start ack receive thread
        ackReceiver.start();

        // read file contents into byte array
        byte[] fileBytes = readFile(fileName);

        // loop over a file contents, breakd
        // into segments, and send over UDP
        boolean fileNotFinished = true;
        int currentIndex = 0;
        byte[] payload;
        int seqNo = 0;

        while(fileNotFinished) {
            // fills a segment with next section
            // of file contents byte array
            // current section to be sent is
            // defined by currentIndex
            // on the last segment, the next
            // section will most likely be
            // smaller than Segment.MAX_PAYLOAD_SIZE so
            // catch exception and finish
            // loop
            try {
                payload = new byte[Segment.MAX_PAYLOAD_SIZE];
                // triggers exception when end of file
                // reached
                for(int i=0; i<payload.length;i++) {
                    payload[i] = fileBytes[currentIndex+i];
                }
                currentIndex = currentIndex + payload.length;
                // checks if another section exists
                // in case Segment.MAX_PAYLOAD_SIZE divides file
                // size exactly (i.e. no exception occurs)
                // if no nore bytes, it will trigger an
                // exception
                byte checkEOF = fileBytes[currentIndex+1];
            } catch (Exception e) {
                System.out.println("End of file reached, sending last segment...");
                payload = new byte[fileBytes.length - currentIndex];
                for(int i=0; i<payload.length;i++) {
                    payload[i] = fileBytes[currentIndex+i];
                }
                // end loop after this packet sent
                fileNotFinished = false;
            }
           
            // send packet and get response
            // (takes into account timeout)
            while(queueFull()) {
                System.out.println("Waiting for window space");
            }

            // add packet to queue window
            try {
                window.add(new Segment(seqNo, payload));
                TxQueueNode node = window.getNode(seqNo);
                node.setStatus(TxQueueNode.SENT);
                // send packet
                sendPacketData(payload, seqNo);
                seqNo++;
            } catch (Exception e) {
                System.out.println("Error adding packet, resending...");
            }

            
            if(!fileNotFinished && window.isEmpty()) {
                try {
                    System.out.println("Terminating...");
                    output.writeByte(0);
                } catch (Exception e) {
                    System.out.println("End of file output error");
                }
            } else {
                while(!window.isEmpty()) {
                }
            }
        }


        // send end of transmission message
        boolean EOTSuccess = TCPEndTransmission();
        if(!EOTSuccess) {
            System.out.println("EOT message failure");
        }

        // close sockets and io streams
        try {
            input.close();
            output.close();
            UDPSocket.close();
            socket.close();
            ackReceiver.stopThread();
            timer.cancel();
        } catch (Exception e) {
            System.out.println("Socket close error");
        }
    }

    public Timer getTimer()
    {
        return timer;
    }

    public int getTimeout()
    {
        return responseTimeout;
    }

    public TxQueue getWindow()
    {
        return window;
    }

    public boolean queueFull()
    {
        return window.isFull();
    }

    public boolean TCPHandshake()
    {
        /* send TCP handshake */

        // set up socket
        socket = null;
        try {
            socket = new Socket(serverName, serverPort);
        } catch (Exception e) {
            System.out.println("Socket initialization error");

            System.out.println(e.getMessage());
        }

        // set up output stream, and send initial handshake
        try {
            output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF(fileName);
        } catch (Exception e) {
            System.out.println("Handshake output error");
        }

        // set up input strea, and wait for handshake response
        byte response = -1;
        try {
            input = new DataInputStream(socket.getInputStream());
            boolean readData = true;
            while(readData) {
                if(input.available() > 0) {
                    response = input.readByte();
                    readData = false;
                }
            }
        } catch (Exception e) {
            System.out.println("Input stream handshake error");
        }

        // return boolean indicating
        // success
        if(response != 0) {
            return false;
        } else {
            return true;
        }
    }

    public boolean TCPEndTransmission()
    {
        // sends termination message to
        // same TCP socket
        try {
            output.writeByte(0);
        } catch (Exception e) {
            System.out.println("EOT byte output error");
            return false;
        }
        return true;
    }

    public byte[] readFile(String filePath)
    {
        // reads file by name from execution directory
        // and returns a byte array with its contents

        // create full path
        filePath = System.getProperty("user.dir") + "/" + filePath;
        
        // initialize values
        byte[] data = null;
        File file = null;

        // check path is correct
        try {
            file = new File(filePath);
        } catch (Exception e) {
            System.out.println("Invalid file path");
        }

        // try reading file into byte array
        String eachLine = null;
        try {
            data = new byte[(int) file.length()];
            FileInputStream fileStream = new FileInputStream(file);
            DataInputStream dataStream = new DataInputStream(fileStream);
            dataStream.read(data);
            fileStream.close();
            dataStream.close();
        } catch (Exception e) {
            // handle any exceptions
            System.out.println("File exception triggered");
            System.out.println("Message: " + e.getMessage());
        }
        // return file contents as byte array
        return data;
    }

    public void sendPacketData(byte[] payload, int seqNo)
    {
        /* main UDP send logic */
        // takes payload byte array and sequence number
        // creates and sends this as a packet to server
        // and waits for ACK until timeout
        // upon timeout, repeat process until ACK
        // received

        // creating a segment with specified payload
        // and sequence number
        Segment seg1 = new Segment(seqNo, payload);
        
        // convert segment to bytes in
        // order to send data
        byte[] sendData = seg1.getBytes();

        // create sender packet from specified segment
        // data, server and server port info
        DatagramPacket sendPacket =  new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);

        // try send packet to server
        try {
            UDPSocket.send(sendPacket);
            // start timer
            timer.schedule(new TimeoutHandler(this, seqNo, payload), responseTimeout);
        } catch (Exception e) {
            System.out.println("Packet send error");
            System.out.println(e.getMessage());
        }



    }


    /**
     * A simple test driver
     * 
     */
    public static void main(String[] args) {
        int window = 10; //segments
        int timeout = 100; // milli-seconds (don't change this value)
        
        String server = "localhost";
        String file_name = "";
        int server_port = 0;
        
        // check for command line arguments
        if (args.length == 4) {
            // either provide 3 parameters
            server = args[0];
            server_port = Integer.parseInt(args[1]);
            file_name = args[2];
            window = Integer.parseInt(args[3]);
        }
        else {
            System.out.println("wrong number of arguments, try again.");
            System.out.println("usage: java FastClient server port file windowsize");
            System.exit(0);
        }

        
        FastClient fc = new FastClient(server, server_port, window, timeout);
        
        System.out.printf("sending file \'%s\' to server...\n", file_name);
        fc.send(file_name);
        System.out.println("file transfer completed.");
    }

}
