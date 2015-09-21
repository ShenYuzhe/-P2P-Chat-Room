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
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

public class clientHeartBeat implements Runnable {
	private final int timePeriod = 30000;
	private String username;
	private int remainTime;

	private String servIp;
	private int servPort;
	private int listenPort;
	private String localIP;
	private Socket socket;

	public clientHeartBeat(String newServIp, int newServPort, int newListenPort) {
		servIp = newServIp;
		servPort = newServPort;
		listenPort = newListenPort;
		remainTime = timePeriod;
		localIP = "";
		try {
			localIP = InetAddress.getLocalHost().toString().split("/")[1];
		} catch (Exception e) {}
	}

	public void setName(String newName) {
		username = newName;
	}

	public void run() {
			while (!clientMain.exitFlag) {
				try {
					Thread.sleep(1000);
				} catch (Exception e) {}
				
				if (clientMain.exitFlag)
					break;
				if (remainTime >= 0) {
					remainTime -= 1000;
					continue;
				}
				remainTime = timePeriod;
				try {
					socket = new Socket(servIp, servPort);
					JSONObject liveJSON = new JSONObject();
					liveJSON.put("username", username);
					liveJSON.put("address", localIP + ":" + listenPort);
					liveJSON.put("type", "LIVE");
					PrintStream out = new PrintStream(socket.getOutputStream());
					out.println(liveJSON.toString());
					socket.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					System.out.println("Server is down");
					System.out.println("Press ENTER to exit");
					clientMain.logout(true, "sender");
				}
			
		}
	}
}