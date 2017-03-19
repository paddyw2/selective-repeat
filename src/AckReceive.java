/* Class: AckReceive
 *
 * This class creates a thread
 * that listens for ack responses
 * from the server
 * When a response is received
 * the associated packet status
 * is updated in the client
 * window
 */

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
        // sets the node status in window
        // from sent to acknowledged
        TxQueueNode node = window.getNode(seqNo);
        node.setStatus(TxQueueNode.ACKNOWLEDGED);
    }

    public void updateWindow()
    {
        // updates window base until an
        // unacked packet is found
        // window can only be updated if
        // base node is acknowledged
        boolean baseIsAcked = true;
        while(baseIsAcked) {
            TxQueueNode node = window.getHeadNode();
            if(node != null && node.getStatus() == TxQueueNode.ACKNOWLEDGED) {
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
        // allows for external halting
        // of thread
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
                // a socket closed exception is expected
                // so only print error if different
                // exception
                if(!e.getMessage().equals("Socket closed"))
                    System.out.println("Ack Receive Error: "+e.getMessage());
            }
        }
    }
}
