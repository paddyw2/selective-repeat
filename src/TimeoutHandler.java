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
        System.out.println("Running timeout method");
        // if packet
        TxQueueNode node = client.getWindow().getNode(seqNo);
        if(node != null && node.getStatus() != TxQueueNode.ACKNOWLEDGED) {
            // resend packet
            System.out.println("Resending packet...");
            client.sendPacketData(payload, seqNo);
        }
    }
}
