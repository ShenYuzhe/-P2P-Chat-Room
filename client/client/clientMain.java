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

public class clientMain {
	private static String servIp = "localhost";
	private static int servPort = 12000;
	private static int listenPort = 8080;
	public static boolean exitFlag = false;

	private static Thread beatThread = null;
	private static Thread listenThread = null;
	private static Thread senderThread = null;
	private static clientHeartBeat heartBeater;
	private static clientReceiver listener;
	private static clientSender sender;


	/**************************************************************************
	This function is called to terminate all the running threads to exit
	**************************************************************************/
	public static void logout(boolean isForce, String stopper) {
		exitFlag = true;
		if (isForce && stopper.equals("sender")) {
			listenThread.stop();
			listener.disconnect();
		}
	}

	public static void startBeat(String username) {
		heartBeater.setName(username);
		beatThread.start();
	}

	public static void saveAddress(JSONObject addressJSON) {
		sender.saveAddress(addressJSON);
	}

	public static void insertPrivateReq(String fromname, JSONObject req) {
		sender.insertPrivateReq(fromname, req);
	}

	public static void main(String[] args) throws JSONException {
		if (args.length >= 2) {
			servIp = args[0];
			servPort = Integer.parseInt(args[1]);
		} else {
			System.out.println("Server Address is not specified, using default address");
		}

		listener = new clientReceiver();
		listenThread = new Thread(listener);
		listenThread.start();
		heartBeater = new clientHeartBeat(servIp, servPort, listener.getListenPort());
		beatThread = new Thread(heartBeater);
		sender = new clientSender(servIp, servPort, listener.getListenPort());
		senderThread = new Thread(sender);
		senderThread.start();

	
	}
}