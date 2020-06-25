package edu.rit.cs;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

/*
 * Class thread which keeps track whether there are any pending file transfers are left
 * for specific peer
 */
public class SendFilesToNewPeersThread extends Thread {
    private KademliaPeer peerInstance;
    private static int rpcID = 0;

    public SendFilesToNewPeersThread(KademliaPeer peerInstance) {
        this.peerInstance = peerInstance;
    }

    @Override
    public void run() {
        while(true) {
            try {
//                Retrieves the latest pending request hashtable
                Map<FileDataStructure, PeerInfo> pendingReq = this.peerInstance.getPendingReq();

                if (pendingReq.size() != 0) {
                    for (FileDataStructure key: pendingReq.keySet()) {
//                        Send all the queued files to their respective peers

                        System.out.println("\nLocal file " + key.getFilename() + "(" + key.getFileHash() +
                                ") will be sent to peer with ID " + pendingReq.get(key).getId()
                                + "\n\n" + KademliaPeer.optionMenu);
                        //        Request mini server to generate a hash code for file.
                        JSONRPC2Session session = new JSONRPC2Session(
                                new URL("http://" + pendingReq.get(key).getUrl()));
                        JSONRPC2Request req = new JSONRPC2Request("sendStoreFile", rpcID);
                        rpcID += 1;
                        HashMap<String, Object> reqOut = new HashMap<>() ;
                        Gson gson = new Gson();
                        String filestruct = gson.toJson(key);
                        reqOut.put("file", filestruct);
                        req.setNamedParams(reqOut);
                        session.send(req);
                        pendingReq.remove(key);
                    }
                }

                Thread.sleep(2000);
            } catch (MalformedURLException | JSONRPC2SessionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}