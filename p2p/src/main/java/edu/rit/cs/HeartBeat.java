package edu.rit.cs;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

/*
 * Heartbeat class which keeps track whether all the peers in own routing table are active in the network
 */
public class HeartBeat extends Thread {
    public static String serverURL = "http://" + Config.SERVER_IP + ":" + Config.SERVER_PORT;
    private KademliaPeer peerInstance;
    private static int rpcID = 0;

    public HeartBeat(KademliaPeer peerInstance) {
        this.peerInstance = peerInstance;
    }

    @Override
    public void run() {
//        Forever loop with 3 seconds halt
        while (true) {
            try {
                Map<String, PeerInfo> routingTable = this.peerInstance.getRoutingTable();
                for(String key: routingTable.keySet()) {
                    if (routingTable.get(key) == null || key == this.peerInstance.getBinaryForm()) continue;

//                    Send online status check request to miniserver
                    JSONRPC2Session session = new JSONRPC2Session(new URL(serverURL));
                    JSONRPC2Request request = new JSONRPC2Request("isPeerOnline", rpcID);
                    rpcID += 1;

                    HashMap<String, Object> reqOut = new HashMap<>();
                    reqOut.put("id", Integer.toString(routingTable.get(key).getId()));
                    request.setNamedParams(reqOut);
                    JSONRPC2Response response = session.send(request);

                    if (response.indicatesSuccess()) {
                        if(!(Boolean) response.getResult()) {
//                            If peer is no longer active, regenerate the routing table
                            this.peerInstance.generateRoutingTable();
                            break;
                        }
                    }
                }
                Thread.sleep(10000);
            } catch (InterruptedException | MalformedURLException | JSONRPC2SessionException e) {
                e.printStackTrace();
            }
        }
    }
}

