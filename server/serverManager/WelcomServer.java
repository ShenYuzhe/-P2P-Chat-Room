package serverManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import org.json.JSONException;

import clientRoom.ClientManager;

/**************************************************************************
Main class for server. This is the only thread that receives message from
clients. Once it receive a message, it will simply deliver it for the 
corresponding client thread to deal with.
**************************************************************************/

public class WelcomServer {
	private static final int BUFSIZE = 32;
	private static int servPort = 12000;
	
	public static ClientManager clientRoom;
	
	public static void main(String[] args) throws IOException, JSONException {
		// TODO Auto-generated method stub
		
		if (args.length >= 1) {
			servPort = Integer.parseInt(args[0]);
		} else {
			System.out.println("Server Port is not specified, so default port is used");
		}
		
		clientRoom = new ClientManager();
		ServerSocket servSock = new ServerSocket(servPort);
		//Thread requestHandleThread = new Thread(requestHandler);
		//requestHandleThread.start();
		while (true) {
			Socket clntSock = servSock.accept();
			SocketAddress clientAddress = clntSock.getRemoteSocketAddress();
			System.out.println("Handling client at" + clientAddress);

			BufferedReader in = new BufferedReader(new InputStreamReader(clntSock.getInputStream()));
			PrintStream out = new PrintStream(clntSock.getOutputStream());
			String reqStr = in.readLine();
			clientRoom.distributeReq(reqStr, clntSock);
			if ("bye".equals(reqStr))
				break;
		}
		servSock.close();
	}
}
