package edu.rit.cs;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class JsonHandler {
	 public static class MiniServerHandler implements RequestHandler {
	 	private static int rpcID = 0;

	     // Reports the method names of the handled requests
		public String[] handledRequests() {
//			List of all the methods registered with Event Handler
		    return new String[]{"online", "offline", "sendPeerNotification",
					"isPeerOnline", "getPeerList", "newPeerCloseBy", "fileHash"};
		}

		// Processes the requests
		public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
//			Retrieve original data from RPC request
			Map<String, Object> myParams = req.getNamedParams();

//			Id of client who requested the method
			int id = Integer.parseInt((String)myParams.get("id"));
			if (req.getMethod().equals("online"))
			{
//				Add peer in the p2p network
				System.out.println("\nNew request : " + req.getMethod());
				System.out.println("Peer " + id + " joined the network");

//				Adding peer in system
				String ip = (String)myParams.get("ip");
				String port = (String)myParams.get("port");
				MiniServer.addPeer(id, ip, port);

//				Send peer its own ID, ipAddress and port number
				Map<String, Object> response = new HashMap<>();
				response.put("clientID", Integer.toString(id));
				response.put("clientIP", ip);
				response.put("clientPort", port);
				return new JSONRPC2Response(response, req.getID());
	         }
			else if (req.getMethod().equals("offline"))
			{
//				Request to remove peer from the system
				System.out.println("\nNew request : " + req.getMethod());
				System.out.println("Peer " + id + " left the network");

//				Removing peer from system
	         	MiniServer.removePeer(id);
				return new JSONRPC2Response(true, req.getID());
	         }
			else if (req.getMethod().equals("isPeerOnline"))
			{
//				Determine whether peer is active using peer ID
				return new JSONRPC2Response(MiniServer.isPeerOnline(id), req.getID());
			}
			else if (req.getMethod().equals("fileHash"))
			{
//				Calculate Hash code of file using filename

//				Generate hashID for filename
				String inputString = (String) myParams.get("filename");
				id = 0;
				for (int i = 0; i < inputString.length(); i++) {
					id = 31 * id + inputString.charAt(i);
				}
				id %= MiniServer.peerLimit;
				id = Math.abs(id);
				return new JSONRPC2Response(Integer.toString(id), req.getID());
			}
			else if (req.getMethod().equals("getPeerList"))
			{
//				Send list of active peer in network

				Gson gson = new Gson();
				String json = gson.toJson(MiniServer.getPeerList());
				return new JSONRPC2Response(json, req.getID());
			} else if (req.getMethod().equals("sendPeerNotification"))
			{
//				Send notification of new peer to all the currently existing peers in network

				for(int key: MiniServer.getPeerList().keySet()) {
					try {
//						Do not notify new peer itself
						if (MiniServer.getPeerList().get(key).getId() == id) continue;

						URL tempURL = new URL("http://" +  MiniServer.getPeerList().get(key).getUrl());
						JSONRPC2Session session = new JSONRPC2Session(tempURL);
						JSONRPC2Request request = new JSONRPC2Request("newPeerCloseBy", rpcID);
						rpcID += 1;

//						Sending details of new peer
						request.setNamedParams(myParams);
						session.send(request);
					} catch (JSONRPC2SessionException | MalformedURLException e) {
						e.printStackTrace();
					}
				}
				return new JSONRPC2Response("None", req.getID());
			}
			else {
		        // Method name not supported
				return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
	         }
	     }
	 }

}
