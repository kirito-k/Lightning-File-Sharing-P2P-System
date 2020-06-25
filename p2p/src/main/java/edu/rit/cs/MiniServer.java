package edu.rit.cs;

/*
 * Author: Devavrat Kalam
 * Language: Java
 * Details: edu.rit.cs.MiniServer System in P2P
 */

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/*
 * edu.rit.cs.MiniServer of P2P system to only keep track of active nodes
 */
public class MiniServer {
	public static int peerLimit = 16;
	private static Map<Integer, PeerInfo> onlinePeers = new HashMap<Integer, PeerInfo>();
	private static final int PORT = 8080;

	public static void addPeer(int peerID, String ipAddres, String port) {
		onlinePeers.put(peerID, new PeerInfo(peerID, ipAddres, port));
	}

	// Class variable getters
	public static void removePeer(int peerID) {
		onlinePeers.remove(peerID);
	}
	public static boolean isPeerOnline(int peerID) {
		return onlinePeers.containsKey(peerID);
	}
	public static Map<Integer, PeerInfo> getPeerList() {
		return new HashMap<Integer, PeerInfo>(onlinePeers);
	}

	/*
	 * Generate ID for new peers
	 */
	public static int generateID(String inputString){
//		Generate a unique ID for Peer within allowed number of peers count
		int id = 0;
		for (int i = 0; i < inputString.length(); i++) {
			id = 31 * id + inputString.charAt(i);
		}
		id %= peerLimit;
		id = Math.abs(id);

//		Make sure this ID doesn't exists already. Change it if it does
		while (true) {
			if (onlinePeers.containsKey(id)) {
				id += 1;
			} else if (id >= peerLimit) {
				id = 0;
			} else {
				break;
			}
		}
		return id;
	}

	/**
	 * A handler thread class.  Handlers are spawned from the listening
	 * loop and are responsible for a dealing with a single peer
	 */
	private static class Handler extends Thread {
		private Socket socket;
		private BufferedReader in;
		private PrintWriter out;
		private Dispatcher dispatcher;

		/**
		 * Constructs a handler thread, squirreling away the socket.
		 */
		public Handler(Socket socket) {
			this.socket = socket;

//			Create a new JSON-RPC 2.0 request dispatcher
			this.dispatcher =  new Dispatcher();

//			Register the "echo", "getDate" and "getTime" handlers with it
			dispatcher.register(new JsonHandler.MiniServerHandler());
		}

		/**
		 * Overwritten thread method
		 */
		public void run() {
			try {
//				Read RPC request
				// Create character streams for the socket.
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);

				// read request
				String line;
				line = in.readLine();
				//System.out.println(line);
				StringBuilder raw = new StringBuilder();
				raw.append("" + line);
				boolean isPost = line.startsWith("POST");
				int contentLength = 0;
				while (!(line = in.readLine()).equals("")) {
					//System.out.println(line);
					raw.append('\n' + line);
					if (isPost) {
						final String contentHeader = "Content-Length: ";
						if (line.startsWith(contentHeader)) {
							contentLength = Integer.parseInt(line.substring(contentHeader.length()));
						}
					}
				}
				StringBuilder body = new StringBuilder();
				if (isPost) {
					int c = 0;
					for (int i = 0; i < contentLength; i++) {
						c = in.read();
						body.append((char) c);
					}
				}
				
				JSONRPC2Request request = JSONRPC2Request.parse(body.toString());


				if (request.getMethod().equals("online")) {
//					Adding peer's additional peer information
					HashMap<String, Object> clientReq = (HashMap<String, Object>)request.getNamedParams();
					String clientIP = this.socket.getInetAddress().toString();
					clientIP = clientIP.substring(1, clientIP.length());
					String clientPort = Integer.toString(this.socket.getPort());
					int clientID = generateID(clientIP + ":" + clientPort);
					clientReq.put("id", Integer.toString(clientID));
					clientReq.put("ip", clientIP);
					clientReq.put("port", clientPort);
				}

//				Processing the request
				JSONRPC2Response resp = dispatcher.process(request, null);

//				Print all the onlinePeers in the system
				if (request.getMethod().equals("online") || request.getMethod().equals("offline")) {
					System.out.println("Current peers in the table:");
					for(int key : onlinePeers.keySet()) {
						System.out.println("PeerID = " + key + " : " + onlinePeers.get(key).getUrl());
					}
				}

				// send response
				out.write("HTTP/1.1 200 OK\r\n");
				out.write("Content-Type: application/json\r\n");
				out.write("\r\n");
				out.write(resp.toJSONString());
				// do not in.close();
				out.flush();
				out.close();
				socket.close();
			} catch (IOException e) {
				System.out.println(e);
			} catch (JSONRPC2ParseException e) {
				e.printStackTrace();
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/*
	 * Main function of edu.rit.cs.MiniServer
	 */
	public static void main(String[] args) throws Exception {
		System.out.println("The server is running...\n------------------");
		ServerSocket listener = new ServerSocket(PORT);
		try {
			while (true) {
				new Handler(listener.accept()).start();
			}
		} finally {
			listener.close();
		}
	}
}
