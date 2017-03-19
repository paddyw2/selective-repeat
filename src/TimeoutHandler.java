/* Class: TimeoutHandler
 *
 * This class creates a timer thread
 * that when executed, checks to see
 * if the associated packet has timed
 * out
 * If the packet has timed out, it is
 * resent - if not, then thread ends
 * silently
 */

import java.util.*;

public class TimeoutHandler extends TimerTask
{
    private FastClient client;
    private int seqNo;
    private byte[] payload;

    public TimeoutHandler(FastClient client, int seqNo, byte[] payload)
    {
        this.client = client;
        this.seqNo = seqNo;
        this.payload = payload;
    }

    public void run()
    {
        // get the node in window corresponding to this
        // timer packet
        TxQueueNode node = client.getWindow().getNode(seqNo);
        // if the packet is not acknowledged then a timeout
        // has occurred
        if(node != null && node.getStatus() != TxQueueNode.ACKNOWLEDGED) {
            // resend packet
            System.out.println("Timeout: Resending packet...");
            client.sendPacketData(payload, seqNo);
        }
    }
}
