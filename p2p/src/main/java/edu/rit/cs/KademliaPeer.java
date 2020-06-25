package edu.rit.cs;

/*
 * Author: Devavrat Kalam
 * Language: Java
 * Details: Kademlia Peers in P2P system
 */

import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;


/*
 * edu.rit.cs.KademliaPeer class based on Kademlia node concept in Peer to Peer system
 */
public class KademliaPeer extends Thread{
    // Unique RPC ID for every communication
    public static int rpcID = 0;
    // Static server IP
    public static String serverURL = "http://" + Config.SERVER_IP + ":" + Config.SERVER_PORT;
    // Peer Instance class variables
    private int peerID;
    private String ipAddress;
    private String port;
    private String binaryForm = "";
    private Map<String, PeerInfo> routingTable;
    private Map<String, FileDataStructure> localFiles = new HashMap<>();
    private Map<FileDataStructure, PeerInfo> pendingReq = new HashMap<>();
    // User options to interact with Peer
    public static String optionMenu = "1. Send File \n2. Retrieve File \n3. Exit";

    // Class variable getters
    public String getBinaryForm() { return binaryForm; }
    public Map<FileDataStructure, PeerInfo> getPendingReq() { return pendingReq; }
    public Map<String, PeerInfo> getRoutingTable() { return routingTable; }
    public Map<String, FileDataStructure> getLocalFiles() { return localFiles; }

    public KademliaPeer(int clientID, String clientIP, String clientPort) throws MalformedURLException, JSONRPC2SessionException {
//        Instantiate class instance
        this.peerID = clientID;
        this.ipAddress = clientIP;
        this.port = clientPort;

//		Generating binary representation of the ID
        while(clientID > 0)
        {
            int y = clientID % 2;
            binaryForm = y + binaryForm;
            clientID = clientID / 2;
        }
        while (binaryForm.length() < 4) binaryForm = "0" + binaryForm;

        System.out.println("You have been added as a new peer in network with peer ID: " + this.peerID);

//        Generate the routing table
        this.generateRoutingTable();
    }

    @Override
    public void run() {
        BufferedWriter writer;
        JSONRPC2Request request;
        JSONRPC2Response response = null;

        try {
//            Socker to interact with incoming RPC requests
            ServerSocket ss = new ServerSocket(Integer.parseInt(this.port));
            while (true) {
                Socket peer = ss.accept();
                writer = new BufferedWriter(new OutputStreamWriter(peer.getOutputStream()));

//                Parse the client incoming HTTP request into JSON RPC request object
                request = this.parseIncomingRPC(peer);
                System.out.println("\nNew method requested: " + request.getMethod());

                FileDataStructure file = null;
                if (!request.getMethod().equals("newPeerCloseBy") &&
                        !request.getMethod().equals("resetRoutingTable")) {
                    HashMap<String, Object> reqOut = (HashMap<String, Object>) request.getNamedParams() ;
                    Gson gson = new Gson();
                    file = gson.fromJson((String) reqOut.get("file"), FileDataStructure.class);
                }

                if (request.getMethod().equals("newPeerCloseBy"))
                {
                    this.newPeerCloseBy(request);
                }
                else if (request.getMethod().equals("resetRoutingTable"))
                {
//                    Generate a new routing table again due to a node going offline.
                    this.generateRoutingTable();
                }
                else if (request.getMethod().equals("sendStoreFile"))
                {
                    this.sendStoreFile(file);
                } else if (request.getMethod().equals("requestedFile")) {
                    this.localFiles.put(file.getFilename(), file);
                    this.printFileContents(file);
                } else if (request.getMethod().equals("searchFile")) {
                    this.searchFile(file);
                }

                System.out.println("\nChoose one of the following options:\n" + optionMenu);
//				Write the output to client
                response = new JSONRPC2Response("None", request.getID());
                writer.write("HTTP/1.1 200 OK\r\n");
                writer.write("Content-Type: application/json\r\n");
                writer.write("\r\n");
                writer.write(response.toJSONString());
                writer.flush();
                writer.close();
                peer.close();
            }
        } catch (IOException | JSONRPC2ParseException | JSONRPC2SessionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
     * Print Current Routing table
     */
    private void printRoutingTable() {
        System.out.println("----- Current Routing Table for Peer:" + this.peerID + " ------");
        for(String key: routingTable.keySet()) {
            if (routingTable.get(key) != null) {
                System.out.println("bitSeq = " + key + "\tPeer ID = " + routingTable.get(key).getId() +
                        " at " + routingTable.get(key).getUrl());
            } else {
                System.out.println("bitSeq = " + key + "\tPeer ID = -");
            }
        }
//        System.out.println("\nChoose one of the following options:\n" + optionMenu);
    }

    /*
     * Print requested file contents
     */
    private void printFileContents(FileDataStructure fileObj) throws IOException {
//        Store file on local machine
        String filename = fileObj.getFilename();
        FileOutputStream fs = new FileOutputStream("./" + filename);
        byte b[] = fileObj.getContent();
        fs.write(b, 0, b.length);

//        Read file and print the contents
        File file = new File("./" + filename);
        Scanner sc = new Scanner(file);
        System.out.println(filename + " contents:");
        while(sc.hasNext()) {
            System.out.println(sc.nextLine());
        }
    }

    /*
     * Change the incoming client's HTTP request and convert it to JSON RPC request form
     */
    public JSONRPC2Request parseIncomingRPC(Socket newClient) throws IOException, JSONRPC2ParseException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(newClient.getInputStream()));

//		Stores complete header structure of HTTP request
        String completeHeader = "";
//		Used for store body length in HTTP request
        int contentLen = 0;
        String line;

//		Read request and gather Header and body length
        while (!(line = reader.readLine()).equals("")) {
            if (line.startsWith("Content-Length: ")) {
                contentLen = Integer.parseInt(line.substring("Content-Length: ".length()));
            }
            completeHeader += line + "\n";
        }

        StringBuilder mainBody = new StringBuilder();
//		Gathers characters in HTTP request body and store them in StringBuilder
        for(int charCount = 0; charCount < contentLen; charCount++) {
            mainBody.append((char) reader.read());
        }
        JSONRPC2Request request = JSONRPC2Request.parse(mainBody.toString());
        return request;
    }

    /*
     * From given file hash code determine the closest peer from routing table
     */
    private PeerInfo nearestFilePeer(int fileHash) {
        int minDistance = Integer.MAX_VALUE;
        PeerInfo nearestPeer = null;
        for (String key: routingTable.keySet()) {
            if (routingTable.get(key) == null) continue;

            int tempDistance = routingTable.get(key).getId() ^ fileHash;
            if (tempDistance < minDistance) {
                minDistance = tempDistance;
                nearestPeer = routingTable.get(key);
            }
        }
        return nearestPeer;
    }

    /*
     * Check whether the new peer added to the peer to peer network should be added to own routing table
     */
    private void newPeerCloseBy(JSONRPC2Request request) throws MalformedURLException, JSONRPC2SessionException, InterruptedException {
        HashMap<String, Object> resOut = (HashMap<String, Object>) request.getNamedParams();
        PeerInfo tempPeer = new PeerInfo(Integer.parseInt((String)resOut.get("id")),
                (String)resOut.get("ipAddress"), (String)resOut.get("port"));

//        finding the common prefix for the requesting peer and own IDs in binary form
        int firstNonMatchIndex = 0;
        while(tempPeer.getBinaryForm().charAt(firstNonMatchIndex) ==
                this.binaryForm.charAt(firstNonMatchIndex)) {
            firstNonMatchIndex += 1;
        }
        String startString = binaryForm.substring(0, firstNonMatchIndex);
        if(binaryForm.charAt(firstNonMatchIndex) == '0') {
            startString += '1';
        } else {
            startString += '0';
        }

//        Compare distances between new peer and the current position holder peer in routing table
        int neighbourPeerDistance = tempPeer.getId() ^ this.peerID;
        int currDistance = Integer.MAX_VALUE;
        if (this.routingTable.get(startString) != null) {
            currDistance = this.routingTable.get(startString).getId() ^ this.peerID;
        }

//        If new peer is closer, replace current table entry
        if (neighbourPeerDistance < currDistance) {
            this.routingTable.put(startString, tempPeer);
            this.printRoutingTable();
        }

//        Checking if the new peer is better for locally stored files(File Id more closer to this new Peer)
//        If it is more closer, send the file to it.
        for (String key: this.localFiles.keySet())
        {
            neighbourPeerDistance = tempPeer.getId() ^ this.localFiles.get(key).getFileHash();
            currDistance = this.peerID ^ this.localFiles.get(key).getFileHash();
            if (neighbourPeerDistance < currDistance) {
                this.pendingReq.put(this.localFiles.get(key), tempPeer);
                this.localFiles.remove(key);
            }
        }
    }

    /*
     * Send notification to all peers in network informing own joining. This will be done through miniserver
     */
    private void sendPeerNotification() throws MalformedURLException, JSONRPC2SessionException {
        URL tempURL = new URL(serverURL);
//        Specifying edu.rit.cs.MiniServer's networking URL
        JSONRPC2Session session = new JSONRPC2Session(tempURL);

//        Send new user request to system
        JSONRPC2Request request = new JSONRPC2Request("sendPeerNotification", rpcID);
        rpcID += 1;

//			Passing User ID in RPC request
        HashMap<String, Object> reqOut = new HashMap<>();
        reqOut.put("id", Integer.toString(this.peerID));
        reqOut.put("ipAddress", this.ipAddress);
        reqOut.put("port", this.port);
        request.setNamedParams(reqOut);

//			Send request to server
        session.send(request);
    }

    /*
     * Generate routing table
     */
    public void generateRoutingTable() throws MalformedURLException, JSONRPC2SessionException {
//        Adding own entry in routing table
        routingTable = new HashMap<String, PeerInfo>();
        routingTable.put(binaryForm, new PeerInfo(this.peerID, this.ipAddress, this.port));

//        Request list of online peers in network
        JSONRPC2Session session = new JSONRPC2Session(new URL(serverURL));
        JSONRPC2Request request = new JSONRPC2Request("getPeerList", rpcID);
        rpcID += 1;

        HashMap<String, Object> reqOut = new HashMap<>();
        reqOut.put("id", Integer.toString(peerID));
        request.setNamedParams(reqOut);

//		Send request to Event Manager and do nothing with incoming response
        JSONRPC2Response response = session.send(request);
        if (response.indicatesSuccess()) {

//            Converting complex result from server back using Gson package
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<Integer, PeerInfo>>(){}.getType();
            HashMap<Integer, PeerInfo> onlinePeers = gson.fromJson((String) response.getResult(), type);

            System.out.println("\nCurrent online peers in the network:");
            for(int key: onlinePeers.keySet()) {
                System.out.println("Peer ID = " + key + " at " + onlinePeers.get(key).getUrl());
            }

//            Generating actual routing table
            for (int index = 0; index < 4; index++) {
//                Store 4 peers for 4 possible patterns
//                (Example, binaryform = 1011. Patterns will be 101x, 10xx, 1xxx, xxxx)
                String startString = binaryForm.substring(0, index);
                if(binaryForm.charAt(index) == '0') {
                    startString += '1';
                } else {
                    startString += '0';
                }

                int minDistance = Integer.MAX_VALUE;
                PeerInfo nearestPeer = null;
                for(int key: onlinePeers.keySet()) {
                    if (onlinePeers.get(key).getBinaryForm().startsWith(startString)) {
                        int tempDistance = key^this.peerID;
                        if (tempDistance < minDistance) {
                            nearestPeer = onlinePeers.get(key);
                            minDistance = tempDistance;
                        }
                    }
                }

//                Add the closest peer entry in the routing table
                routingTable.put(startString, nearestPeer);
            }

//            Send notification to all the nodes of own existence in the network
            this.sendPeerNotification();
            this.printRoutingTable();
        } else {
            response.getError().getMessage();
        }
    }

    /*
     * Exit peer to peer network
     */
    public void exit() throws JSONRPC2SessionException, IOException, InterruptedException {
        JSONRPC2Session session = new JSONRPC2Session(new URL(serverURL));
        JSONRPC2Request request = new JSONRPC2Request("offline", rpcID);
        rpcID += 1;

        HashMap<String, Object> reqOut = new HashMap<>();
        reqOut.put("id", Integer.toString(peerID));
        request.setNamedParams(reqOut);

//		Send request to edu.rit.cs.MiniServer about offline status and do nothing with incoming response
        session.send(request);

        System.out.println("\nGood Bye");
        System.exit(0);
    }

    /*
     * Search for requested file
     */
    private void searchFile(FileDataStructure fileObj) throws IOException, JSONRPC2SessionException {
//        Determine the nearest peer for this filehash
        PeerInfo nearestPeer = this.nearestFilePeer(fileObj.getFileHash());

        if (nearestPeer.getId() == this.peerID) {
//            If file is present locally
            if (fileObj.getRequestingPeer().getId() == this.peerID)
            {
//                If file request is initiated locally, print file contents

                printFileContents(this.localFiles.get(fileObj.getFilename()));
            } else {
//                Send file to the request initiator peer directly
                System.out.println("File " + fileObj.getFilename() + "(FileHash:" + fileObj.getFileHash()
                        + ") is sent to Peer(" + fileObj.getRequestingPeer().getId() + ")");
                JSONRPC2Session session = new JSONRPC2Session(
                        new URL("http://" + fileObj.getRequestingPeer().getUrl()));
                JSONRPC2Request req = new JSONRPC2Request("requestedFile", rpcID);
                rpcID += 1;
                HashMap<String, Object> reqOut = new HashMap<>() ;
                Gson gson = new Gson();
                String filestruct = gson.toJson(this.localFiles.get(fileObj.getFilename()));
                reqOut.put("file", filestruct);
                req.setNamedParams(reqOut);
                session.send(req);
            }
        } else {
//            If file does not exists locally, forward the request to the closest peer from own routing table
            System.out.println("File " + fileObj.getFilename() + "(FileHash:" + fileObj.getFileHash()
                    + ") search request forwarded to Peer(" + nearestPeer.getId() + ")");
            JSONRPC2Session session = new JSONRPC2Session(new URL("http://" + nearestPeer.getUrl()));
            JSONRPC2Request request = new JSONRPC2Request("searchFile", rpcID);
            rpcID += 1;
            HashMap<String, Object> reqOut = new HashMap<>();
            Gson gson = new Gson();
            String filestruct = gson.toJson(fileObj);
            reqOut.put("file", filestruct);
            request.setNamedParams(reqOut);
            session.send(request);
        }
    }

    /*
     * Store or send new file
     */
    private void sendStoreFile(FileDataStructure fileObj) throws IOException, JSONRPC2SessionException {
        System.out.println("New File storing request: " + fileObj.getFilename() + "(" + fileObj.getFileHash() + ")");
//        Determine the nearest peer for this filehash
        PeerInfo nearestPeer = this.nearestFilePeer(fileObj.getFileHash());

        if (nearestPeer.getId() == this.peerID) {
//            If current peer is the nearest peer, store this the file at this peer.

            System.out.println("File " + fileObj.getFilename() + "(FileHash:" + fileObj.getFileHash()
                    + ") will be stored on this Peer(" + this.peerID + ")");
            FileOutputStream fos = new FileOutputStream("./" + fileObj.getFilename());
            fos.write(fileObj.getContent(), 0, fileObj.getContent().length);
            localFiles.put(fileObj.getFilename(), fileObj);
        } else {
//            Send file to the nearest peer
            System.out.println("File " + fileObj.getFilename() + "(FileHash:" + fileObj.getFileHash()
                    + ") is sent to Peer(" + nearestPeer.getId() + ")");
            JSONRPC2Session session = new JSONRPC2Session(new URL("http://" + nearestPeer.getUrl()));
            JSONRPC2Request request = new JSONRPC2Request("sendStoreFile", rpcID);
            rpcID += 1;
            HashMap<String, Object> reqOut = new HashMap<>() ;
            Gson gson = new Gson();
            String filestruct = gson.toJson(fileObj);
            reqOut.put("file", filestruct);
            request.setNamedParams(reqOut);
            session.send(request);
        }
    }

    /*
     * Request miniserver for file hash code based on the filename
     */
    public int getFileHash(String filename) throws MalformedURLException, JSONRPC2SessionException {
//        Request mini server to generate a hash code for file.
        JSONRPC2Session session = new JSONRPC2Session(new URL(serverURL));
        JSONRPC2Request request = new JSONRPC2Request("fileHash", rpcID);
        rpcID += 1;
        HashMap<String, Object> reqOut = new HashMap<>() ;
        reqOut.put("id", Integer.toString(peerID));
        reqOut.put("filename", filename);
        request.setNamedParams(reqOut);
        JSONRPC2Response response = session.send(request);

//        Store file hash and its details in custom edu.rit.cs.FileDataStructure
        return Integer.parseInt((String) response.getResult());
    }

    /*
     * This function handles interactions with users
     */
    public void serviceManager() throws IOException, JSONRPC2SessionException, InterruptedException {
        String selectedOption;
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            Thread.sleep(1000);
            System.out.println("\nChoose one of the following options:\n" + optionMenu);
//			Forever Loop for User interaction
            selectedOption = userInput.readLine();
            if (selectedOption.equals("1")) {
//                Store new file

                System.out.print("Enter file name: ");
                String filename = userInput.readLine();

                int fileHash = this.getFileHash(filename);
                FileInputStream fis = new FileInputStream("./" + filename);
                byte b[] = new byte[20002];
                fis.read(b, 0, b.length);
                FileDataStructure fileObj = new FileDataStructure(fileHash, filename, b, null);
                sendStoreFile(fileObj);
            }
            else if (selectedOption.equals("2")) {
//                Retrieve a file

                System.out.print("Enter file name: ");
                String filename = userInput.readLine();

                int fileHash = this.getFileHash(filename);
                FileDataStructure fileObj = new FileDataStructure(fileHash, filename, null,
                        this.routingTable.get(this.binaryForm));
                searchFile(fileObj);
            }
            else if (selectedOption.equals("3")) exit();
            else System.out.println("Incorrect input");
        }
    }

    /*
     * Main function of Kademlia Peer
     */
    public static void main(String[] args) throws IOException, JSONRPC2SessionException, InterruptedException {
//        Specifying edu.rit.cs.MiniServer's networking URL
        JSONRPC2Session session = new JSONRPC2Session(new URL(serverURL));

//        Send new user request to system
        JSONRPC2Request request = new JSONRPC2Request("online", rpcID);
        rpcID += 1;

//			Passing User ID in RPC request
        HashMap<String, Object> reqOut = new HashMap<>();
        request.setNamedParams(reqOut);

//			Send request to server
        JSONRPC2Response response = session.send(request);

        if (response.indicatesSuccess()) {
            HashMap<String, String> serverResult =  (HashMap<String, String>) response.getResult();
//            Create Peer instance
            KademliaPeer peerInstance = new KademliaPeer(Integer.parseInt(serverResult.get("clientID")),
                    serverResult.get("clientIP"), serverResult.get("clientPort"));
            peerInstance.start();

//            Create a hearbeat thread
            new HeartBeat(peerInstance).start();
//            Create a pending task threads for file sending
            new SendFilesToNewPeersThread(peerInstance).start();

//            Interact with user
            peerInstance.serviceManager();
        }
    }
}