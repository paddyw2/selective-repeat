import java.io.*;
import java.net.*;
import java.util.*;

public class AckReceive extends Thread
{
    private DatagramSocket socket;
    private TxQueue window;
    private boolean runThread;

    public AckReceive(DatagramSocket socket, TxQueue window)
    {
        this.socket = socket;
        this.window = window;
    }

    public void updateAck(int seqNo)
    {
        TxQueueNode node = window.getNode(seqNo);
        node.setStatus(TxQueueNode.ACKNOWLEDGED);

    }

    public void updateWindow()
    {
        boolean baseIsAcked = true;
        while(baseIsAcked) {
            TxQueueNode node = window.getHeadNode();
            if(node.getStatus() == TxQueueNode.ACKNOWLEDGED) {
                try {
                    window.remove();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } else {
                baseIsAcked = false;
            }
        }
    }

    public void stopThread()
    {
        System.out.println("Stopping thread...");
        runThread = false;
    }

    public void run()
    {
        runThread = true;
        // create receiving packet
        byte[] receiveData = new byte[4];
        DatagramPacket pkt = new DatagramPacket(receiveData, receiveData.length);

        while(runThread) {
            // wait for server response
            try {
                socket.receive(pkt);
                // if packet received, and
                // correct ACK, break loop
                Segment ack = new Segment(pkt);
                int ackNo = ack.getSeqNum();
                // update ack in queue
                updateAck(ackNo);
                // update window base
                updateWindow();
            } catch (Exception e) {
                System.out.println("No packets received");
            }
        }

    }
}
