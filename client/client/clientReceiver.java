package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import org.json.JSONException;
import org.json.JSONObject;

public class clientReceiver implements Runnable{
	private boolean isSpecified = false;
	private int listenPort = 8080;
	private int maxPorts = 12000;
	private ServerSocket listenSock = null;
	private Socket servSock = null;

	BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

	public clientReceiver(int newlistenPort) {
		isSpecified = true;
		try {
			listenSock = new ServerSocket(newlistenPort);
		} catch (Exception e) {}
		
		listenPort = newlistenPort;
		System.out.println("new Listener");
		
	}

	public clientReceiver() {
		try {
			listenSock = new ServerSocket();
			listenSock.bind(null);
		} catch (Exception e) {

		}

		if (listenSock != null)
			listenPort = listenSock.getLocalPort();
	}

	public int getListenPort() {
		return listenPort;
	}

	/**************************************************************************
	This function resolves the JSON received from server or other clients, and
	display information according to the JSON message.
	**************************************************************************/
	private void displayMSG(JSONObject msgJSON) throws JSONException {
		String type = msgJSON.getString("type");
		if (type.equals("blockby")) {
			System.out.println("You are blocked by " + msgJSON.getString("fromname"));
		} else if (type.equals("unblockby")) {
			System.out.println("You are unblocked by " + msgJSON.getString("fromname"));
		} else if (type.equals("forcelogout")) {
			System.out.println("Your account is online at another place, press ENTER to exit");
			System.out.println("Exiting...");
		} else if (type.equals("loginnotify")) {
			System.out.println(msgJSON.getString("fromname") + " is online");
		} else if (type.equals("logoutnotify")) {
			System.out.println(msgJSON.getString("fromname") + " is offline");
		} else if (type.equals("rejectby")) {
			System.out.println("Your message to " + msgJSON.getString("fromname") + " is blocked");
		} else if (type.equals("messagefrom")) {
			String fromname = msgJSON.getString("fromname");
			String msgContent = msgJSON.getString("message");
			System.out.println(fromname + ": " + msgContent);
		} else if (type.equals("private")) {
			String fromname = msgJSON.getString("username");
			String msgContent = msgJSON.getString("message");
			System.out.println(fromname + "(private): " + msgContent);
		} else if (type.equals("msgreply")) {
			String fromname = msgJSON.getString("fromname");
			System.out.println("messaget to " + fromname + " is received");
		} else if (type.equals("logout")) {
			System.out.println("Exiting...");
		} else if (type.equals("getaddressby")) {
			String fromname = msgJSON.getString("fromname");
			clientMain.insertPrivateReq(fromname, msgJSON);
			System.out.println(fromname + " wants to talk to you privately");
		} else if (type.equals("acceptby")) {
			System.out.println("Your private invitation to " + msgJSON.getString("fromname") + " is accepted");
			String fromname = msgJSON.getString("fromname");
			clientMain.saveAddress(msgJSON);
		} else if (type.equals("broadcastfrom")) {
			String fromname = msgJSON.getString("fromname");
			String msgContent = msgJSON.getString("message");
			System.out.println(fromname + "(broadcast):" + msgContent);
		}
	}

	/**************************************************************************
	This function was used for replying to server, and server will send a message
	reply to the original sender. But it is now abandoned
	**************************************************************************/
	private void replyMSG(PrintStream out, JSONObject msgJSON) throws JSONException, IOException{
		String type = msgJSON.getString("type");
		JSONObject respJSON = new JSONObject();
		if (type.equals("messagefrom")) {
			respJSON.put("type", "msgreply");
			out.println(respJSON.toString());
		}
	}

	/**************************************************************************
	Main loop for sender
	**************************************************************************/
	public void run() {
		while (!clientMain.exitFlag) {
			try {
				servSock = listenSock.accept();
				SocketAddress clientAddress = servSock.getRemoteSocketAddress();
	
				BufferedReader in = new BufferedReader(new InputStreamReader(servSock.getInputStream()));
				PrintStream out = new PrintStream(servSock.getOutputStream());
				String message = in.readLine();
				JSONObject msgJSON = new JSONObject(message);
				//replyMSG(out, msgJSON);
				//System.out.print(">");
				displayMSG(msgJSON);
				System.out.print(">");
				servSock.close();
				if (msgJSON.getString("type").equals("logout")) {
					clientMain.logout(false, "listener");
				} else if (msgJSON.getString("type").equals("forcelogout")) {
					System.out.println("Press ENTER to exit");
					clientMain.logout(true, "listener");
				}
			} catch (Exception e) {
				
			}
		}
		try {
			listenSock.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void disconnect() {
		try {
			if (listenSock != null)
				listenSock.close();
			if (servSock != null)
				servSock.close();
		} catch (Exception e) {}
	}
}