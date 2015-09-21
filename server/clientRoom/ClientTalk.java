package clientRoom;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import serverManager.WelcomServer;

/**************************************************************************
This class deals with communication to each specific client.
private variable "queue" is the message queue for this specific client.
Every time ClientManager Class tries to distribute a message to this
specific client, it need to insert message to this queue.
This queue works in a producer and consumer pattern where ClientManager
is the message producer while this specific client is the consumer.
**************************************************************************/

public class ClientTalk implements Runnable{
	private BlockingQueue<reqType> queue = new LinkedBlockingQueue<reqType>(Integer.MAX_VALUE);
	private ClientObj client;
	
	private Socket clntSocket;
	public String clntIp;
	public int ClientListenPort;
	
	private final static int TotalWrong = 3;
	private int WrongTimes = 0;
	private boolean loggedin = false;
	private long lastBeat;
	private boolean isRun = true;
	
	private PrintStream clntOut;
	private BufferedReader clntIn;
	
	
	public ClientTalk(ClientObj newClient, Socket newSocket, String Address) {
		client = newClient;
		clntSocket = newSocket;
		String[] IpParser = Address.split(":");
		ClientListenPort = Integer.parseInt(IpParser[1]);
		clntIp = IpParser[0];
		updateBeat();
		try {
			clntOut = new PrintStream(clntSocket.getOutputStream());
			clntIn = new BufferedReader(new InputStreamReader(clntSocket.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**************************************************************************
	This functions is the interface for ClientManager Class to distribute a
	message that belongs to this client to this client.
	**************************************************************************/
	public void insertRequest (reqType newReq) {
		try {
			queue.put(newReq);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private reqType getRequest() {
		reqType topReq = null;
		try {
			topReq = queue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return topReq;
	}
	
	/**************************************************************************
	This functions is called when a client is logged out. If there is still
	messages not handled in the message queue. All these messages should be
	move to the client's message queue. When this client login later, it will
	received all these messages without worries about message missing.
	**************************************************************************/
	private void moveIntoMailbox() {
		while (!queue.isEmpty()) {
			try {
				reqType remainReq = queue.take();
				client.leaveMessage(remainReq);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**************************************************************************
	When a client logged in, server needs to move all the messages from its 
	mail box into the message queue.
	**************************************************************************/
	private void moveOutofMailbox() {
		client.moveIntoEventQ(this);
	}
	
	/**************************************************************************
	This functions packed all the login behaviors
	**************************************************************************/
	private void doLogout(reqType msgReq, boolean isForce) throws JSONException {
		if (!isForce) {
			WelcomServer.clientRoom.setOffline(client.userName, clntIp, ClientListenPort);
			WelcomServer.clientRoom.broadcast(msgReq);
		}
		moveIntoMailbox();
		this.isRun = false;
	}
	
	private JSONObject logout(reqType msgReq, boolean isForce) {
		JSONObject respJSON = new JSONObject();
		try {
			doLogout(msgReq, isForce);
			respJSON.put("sendType", "logout");
			
			if (isForce)
				respJSON.put("type", "forcelogout");
			else
				respJSON.put("type", "logout");
		} catch (JSONException e) {
			System.out.println("JSON exception");
		}
		return respJSON;
	}
	
	/**************************************************************************
	Resolves all the JSON message from this client or other client. Do the 
	actions according to the JSON message and return the corresponding response
	JSON.
	For example if it's a "from" type message it means this is a message from
	other clients.
	Otherwise the message is the message from this client.
	**************************************************************************/
	private JSONObject reqHandler(JSONObject reqJSON) throws JSONException {
		reqType reqPackage = new reqType();
		JSONObject msgJSON = new JSONObject();
		JSONObject respJSON = null;
		//reqPackage.socket = clntSocket;
		String type = reqJSON.getString("type");
		if (type.equals("login")) {
			String password = reqJSON.getString("password");
			respJSON = new JSONObject();
			if (password.equals(client.password)) {
				doLogin();
				respJSON.put("type", "success");
			} else {
				WrongTimes++;
				if (WrongTimes < TotalWrong) {
					respJSON.put("type", "invalid password");
				} else {
					WelcomServer.clientRoom.leaveLoginList(clntIp + ":" + ClientListenPort);
					WelcomServer.clientRoom.setBlock(client.userName);
					respJSON.put("type", "serverBlock");
					this.isRun = false;
				}
			}
			respJSON.put("sendType", "response");
			updateBeat();
		} else if (type.equals("message")) {
			ClientObj toClient = WelcomServer.clientRoom.getClientByName(reqJSON.getString("toname"));
			if (toClient == null) {
				respJSON = new JSONObject();
				respJSON.put("type", "error");
				respJSON.put("reason", "user not exist");
			} else if (WelcomServer.clientRoom.isInBlackList(toClient, reqJSON.getString("username"))) {
				respJSON = new JSONObject();
				respJSON.put("type", "rejectby");
				respJSON.put("fromname", reqJSON.getString("toname"));
			} else {
				msgJSON.put("type", "messagefrom");
				msgJSON.put("fromname", reqJSON.getString("username"));
				msgJSON.put("toname", reqJSON.getString("toname"));
				msgJSON.put("message", reqJSON.getString("message"));
				reqPackage.reqJSON = msgJSON;
				respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
				respJSON.put("fromname", reqJSON.getString("toname"));
			}
			respJSON.put("sendType", "response");
		} else if (type.equals("private")) {
			if (WelcomServer.clientRoom.isUserOnline(reqJSON.getString("toname"))) {
				JSONObject addressJSON = WelcomServer.clientRoom.getIpbyName(reqJSON.getString("toname"), client.userName);
				String targetAddress =  addressJSON.getString("IP") + ":" + addressJSON.getInt("Port");
				String privateAddress = reqJSON.getString("privateAddress");
				if (!targetAddress.equals(privateAddress)) {
					respJSON = new JSONObject();
					respJSON.put("type", "changeIP");
					respJSON.put("toname", reqJSON.getString("toname"));
				} else {
					msgJSON.put("type", "privatefrom");
					msgJSON.put("fromname", reqJSON.getString("username"));
					msgJSON.put("toname", reqJSON.getString("toname"));
					msgJSON.put("message", reqJSON.getString("message"));
					msgJSON.put("address", reqJSON.getString("address"));
					reqPackage.reqJSON = msgJSON;
					respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
					respJSON.put("toname", reqJSON.getString("toname"));
				}
			} else {
				msgJSON.put("type", "privatefrom");
				msgJSON.put("fromname", reqJSON.getString("username"));
				msgJSON.put("toname", reqJSON.getString("toname"));
				msgJSON.put("message", reqJSON.getString("message"));
				msgJSON.put("address", reqJSON.getString("address"));
				reqPackage.reqJSON = msgJSON;
				respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
				respJSON.put("toname", reqJSON.getString("toname"));
			}
			respJSON.put("sendType", "response");
		} else if (type.equals("privatefrom")){
			respJSON = new JSONObject();
			respJSON.put("fromname", reqJSON.getString("fromname"));
			respJSON.put("toname", reqJSON.getString("toname"));
			respJSON.put("message", reqJSON.getString("message"));
			String fromname = reqJSON.getString("fromname");
			if (this.client.isInBlackList(fromname)) {
				respJSON.put("sendType", "ignore");
			} else {
				respJSON.put("type", "private");
				respJSON.put("username", fromname);
				respJSON.put("sendType", "from");
			}
		}else if (type.equals("broadcast")) {
			msgJSON.put("type", "broadcastfrom");
			msgJSON.put("fromname", reqJSON.getString("username"));
			msgJSON.put("message", reqJSON.getString("message"));
			reqPackage.reqJSON = msgJSON;
			respJSON = WelcomServer.clientRoom.broadcast(reqPackage);
			respJSON.put("sendType", "response");
		} else if (type.equals("broadcastfrom")) {
			if (client.isInBlackList(reqJSON.getString("fromname"))) {
				respJSON = new JSONObject();
				respJSON.put("sendType", "ignore");
			} else {
				respJSON = reqJSON;
				respJSON.put("sendType", "from");
			}
		} else if (type.equals("block")) {
			if (reqJSON.getString("toname").equals(client.userName)) {
				respJSON = new JSONObject();
				respJSON.put("type", "error");
				respJSON.put("reason", "You cannot block yourself");
			} else if (client.isInBlackList(reqJSON.getString("toname"))) {
				respJSON = new JSONObject();
				respJSON.put("type", "error");
				respJSON.put("reason", "user " + reqJSON.getString("toname") + "already in blaclist");
			} else {
				client.addBlackList(reqJSON.getString("toname"));
				msgJSON.put("type", "blockby");
				msgJSON.put("fromname", reqJSON.getString("username"));
				msgJSON.put("toname", reqJSON.getString("toname"));
				reqPackage.reqJSON = msgJSON;
				respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
				respJSON.put("type", "blockto");
				respJSON.put("toname", reqJSON.getString("toname"));
			}
			respJSON.put("sendType", "response");
		} else if (type.equals("unblock")) {
			if (!client.isInBlackList(reqJSON.getString("toname"))) {
				respJSON = new JSONObject();
				respJSON.put("type", "error");
				respJSON.put("reason", "user " + reqJSON.getString("toname") + "not in blaclist");
			} else {
				client.removeBlackList(reqJSON.getString("toname"));
				msgJSON.put("type", "unblockby");
				msgJSON.put("fromname", reqJSON.getString("username"));
				msgJSON.put("toname", reqJSON.getString("toname"));
				reqPackage.reqJSON = msgJSON;
				respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
			}
			respJSON.put("sendType", "response");
		} else if (type.equals("logout")) {
			msgJSON.put("type", "logoutnotify");
			msgJSON.put("fromname", reqJSON.getString("username"));
			reqPackage.reqJSON = msgJSON;
			respJSON = logout(reqPackage, false);
		} else if (type.equals("forcelogout")) {
			msgJSON.put("type", "logoutnotify");
			msgJSON.put("fromname", client.userName);
			reqPackage.reqJSON = msgJSON;
			respJSON = logout(reqPackage, true);
		} else if (type.equals("getaddress")) {
			ClientObj toClient = WelcomServer.clientRoom.getClientByName(reqJSON.getString("toname"));
			if (toClient == null) {
				respJSON = new JSONObject();
				respJSON.put("type", "error");
				respJSON.put("reason", "user not exist");
			} else if (WelcomServer.clientRoom.isInBlackList(toClient, reqJSON.getString("username"))) {
				respJSON = new JSONObject();
				respJSON.put("type", "blockby");
				respJSON.put("fromname", reqJSON.getString("toname"));
			} else {
				msgJSON.put("type", "getaddressby");
				msgJSON.put("toname", reqJSON.getString("toname"));
				msgJSON.put("fromname", client.userName);
				msgJSON.put("address", reqJSON.getString("address"));
				reqPackage = new reqType();
				reqPackage.reqJSON = msgJSON;
				respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
				respJSON.put("fromname", reqJSON.getString("toname"));
			}
			respJSON.put("sendType", "response");
		} else if (type.equals("getaddressby")) {
			respJSON = reqJSON;
			respJSON.put("sendType", "from");
		} else if (type.equals("accept")) {
			msgJSON = reqJSON;
			msgJSON.put("fromname", client.userName);
			msgJSON.put("type", "acceptby");
			msgJSON.remove("username");
			reqPackage.reqJSON = msgJSON;
			respJSON = WelcomServer.clientRoom.messageCenter(reqPackage);
			respJSON.put("sendType", "response");
		} else if (type.equals("acceptby")) {
			respJSON = reqJSON;
			respJSON.put("sendType", "from");
		} else if (type.equals("messagefrom")) {
			if (client.isInBlackList(reqJSON.getString("fromname"))) {
				msgJSON.put("type", "rejectby");
				msgJSON.put("fromname", client.userName);
				msgJSON.put("toname", reqJSON.getString("fromname"));
				reqPackage.reqJSON = msgJSON;
				WelcomServer.clientRoom.messageCenter(reqPackage);
				respJSON = new JSONObject();
				respJSON.put("sendType", "ignore");
			} else {
				respJSON = reqJSON;
				respJSON.put("sendType", "from");
			}
		} else if (type.equals("msgreply")) {
			respJSON = reqJSON;
			respJSON.put("sendType", "from");
		} else if (type.equals("blockby")) {
			respJSON = reqJSON;
			respJSON.put("sendType", "from");
		} else if (type.equals("unblockby")) {
			respJSON = reqJSON;
			respJSON.put("sendType", "from");
		} else if (type.equals("rejectby")) {
			respJSON = reqJSON;
			respJSON.put("sendType", "from");
		} else if (type.equals("loginnotify")) {
			if (client.isInBlackList(reqJSON.getString("fromname"))) {
				respJSON = new JSONObject();
				respJSON.put("sendType", "ignore");
			} else {
				respJSON = reqJSON;
				respJSON.put("sendType", "from");
			}
		} else if (type.equals("logoutnotify")) {
			if (client.isInBlackList(reqJSON.getString("fromname"))) {
				respJSON = new JSONObject();
				respJSON.put("sendType", "ignore");
			} else {
				respJSON = reqJSON;
				respJSON.put("sendType", "from");
			}
		} else if (type.equals("online")) {
			respJSON = new JSONObject();
			respJSON.put("type", "onlinelist");
			respJSON.put("sendType", "response");
			respJSON.put("onlinelist", WelcomServer.clientRoom.getOnlineList());
		} else if (type.equals("LIVE")) {
			updateBeat();
			respJSON = new JSONObject();
			respJSON.put("type", "success");
			respJSON.put("sendType", "ignore");
		} else if (type.equals("timeout")) {
			msgJSON.put("type", "logoutnotify");
			msgJSON.put("fromname", client.userName);
			reqType msgReq = new reqType();
			msgReq.reqJSON = msgJSON;
			logout(msgReq, false);
			respJSON = new JSONObject();
			respJSON.put("sendType", "ignore");
		} else if (type.equals("login_timeout")) {
			respJSON = new JSONObject();
			respJSON.put("type", "logout");
			respJSON.put("sendType", "ignore");
			this.isRun = false;
			WelcomServer.clientRoom.leaveLoginList(clntIp + ":" + ClientListenPort);
		} else {}
		return respJSON;
	}
	
	JSONObject getIpAddress(String fromname) {
		JSONObject respJSON = new JSONObject();
		try {
			if (client.isInBlackList(fromname)) {
				respJSON.put("type", "blockby");
				respJSON.put("fromname", client.userName);
			} else {
				respJSON.put("type", "IpAddress");
				respJSON.put("IP", clntIp);
				respJSON.put("Port", ClientListenPort);
				respJSON.put("fromname", client.userName); 
			}
		} catch (Exception e) {}
		return respJSON;
	}
	
	/**************************************************************************
	This functions is used for a very case. For example when client A disconnet
	from IP_0 and times out. And another person login at IP_1. When client A
	reconnect to IP_0 and still tries to send message to server, this message
	is not valid. So every time server should check the IP Address and port
	from a message to verify whether this message is actually from this client.
	**************************************************************************/
	boolean checkAddress(String address) {
		boolean isValid = false;
		String[] IpParser = address.split(":");
		String Ip = IpParser[0];
		int Port = Integer.parseInt(IpParser[1]);
		if (Ip.equals(clntIp) && Port == ClientListenPort)
			isValid = true;
		return isValid;
	}
	
	/**************************************************************************
	This functions was used to inform the message sender that the message
	is actually received by the recevier. But it is now abandoned.
	**************************************************************************/
	private void waitReply(Socket clntSock, JSONObject msgJSON) throws IOException, JSONException {
		String type = msgJSON.getString("type");
		if (type.equals("messagefrom")) {
			BufferedReader in = new BufferedReader(new InputStreamReader(clntSock.getInputStream()));
			String replyMSG = in.readLine();
			String toname = msgJSON.getString("fromname");
			String fromname = client.userName;
			JSONObject replyJSON = new JSONObject(replyMSG);
			reqType replyReq = new reqType();
			replyReq.reqJSON = replyJSON;
			replyJSON.put("toname", toname);
			replyJSON.put("fromname", fromname);
			WelcomServer.clientRoom.messageCenter(replyReq);
		}
	}
	
	/**************************************************************************
	This is the only place for server to communicate a client through socket.
	According to the type of request JSON. If the the request needs response, 
	server will response the JSON returned by "reqHandler" to that client and
	close the socket.
	If this is a "from" type JSON. This means it's a message from other client.
	response Executer will create a new socket to forward the message to that
	client and close the socket.
	If this is an "ignore" type JSON. This means it's a message from other
	client, but that client is in black list, so simply ignore it.
	**************************************************************************/
	private void responseExecuter(reqType reqPackage) throws JSONException {
		Socket respSocket = reqPackage.socket;
		if (respSocket != null) {
			String[] IpParser = respSocket.getInetAddress().toString().split(":");
			String respIp = IpParser[0].substring(1);
			String address = reqPackage.reqJSON.getString("address");
			if (!checkAddress(address)) {
				JSONObject logoutJSON = new JSONObject();
				logoutJSON.put("type", "ExceptionLogout");
				logoutJSON.put("reason", "already online at other place");
				try {
					PrintStream out = new PrintStream(respSocket.getOutputStream());
					out.println(logoutJSON.toString());
				} catch (Exception e) {}
				return;
			}
		}
		JSONObject respJSON = reqHandler(reqPackage.reqJSON);
		Socket sendSocket = null;
		System.out.println(ClientListenPort);
		System.out.println(clntIp);
		try {
			if (respJSON.getString("sendType").equals("ignore"))
				return;
			if (respSocket != null) {
				PrintStream out = new PrintStream(respSocket.getOutputStream());
				out.println(respJSON.toString());
			}
			
			if (respSocket == null) {
				sendSocket = new Socket(clntIp, ClientListenPort);
				PrintStream out = new PrintStream(sendSocket.getOutputStream());
				out.println(respJSON.toString());
				//waitReply(sendSocket, respJSON);
			} else if (respJSON.getString("sendType").equals("logout")) {
				sendSocket = new Socket(clntIp, ClientListenPort);
				PrintStream out = new PrintStream(sendSocket.getOutputStream());
				out.println(respJSON.toString());
			}
		} catch (IOException e) {
			client.leaveMessage(reqPackage);
			reqType reqExcept = new reqType();
			JSONObject msgJSON = new JSONObject();
			msgJSON.put("type", "logoutnotify");
			msgJSON.put("fromname", this.client.userName);
			reqExcept.reqJSON = msgJSON;
			respJSON = logout(reqExcept, false);
		} finally {
			try {
				if (respSocket != null)
					respSocket.close();
				if (sendSocket != null)
					sendSocket.close();
			} catch (Exception e) {}
		}
	}
	
	private void updateBeat() {
		lastBeat = System.currentTimeMillis();
	}
	
	public long getLastBeat() {
		return lastBeat;
	}
	
	/**************************************************************************
	Encapsulate all the actions for login.
	**************************************************************************/
	private void doLogin() {
		kickDup();
		moveOutofMailbox();
		updateBeat();
		WelcomServer.clientRoom.leaveLoginList(clntIp + ":" + ClientListenPort);
		WelcomServer.clientRoom.setOnline(client.userName, this);
		JSONObject loginNotify = new JSONObject();
		reqType notifyReq = new reqType();
		try {
			loginNotify.put("type", "loginnotify");
			loginNotify.put("fromname", client.userName);
			notifyReq.reqJSON = loginNotify;
			WelcomServer.clientRoom.broadcast(notifyReq);
		} catch (Exception e) {}
	}
	
	/**************************************************************************
	When another user is Online using the same username, kicked it off.
	**************************************************************************/
	private void kickDup() {
		JSONObject kickJSON = new JSONObject();
		reqType kickReq = new reqType();
		try {
			kickJSON.put("type", "forcelogout");
			kickJSON.put("toname", client.userName);
			kickReq.reqJSON = kickJSON;
			WelcomServer.clientRoom.messageCenter(kickReq);
			WelcomServer.clientRoom.kickByName(client.userName);
		} catch (Exception e) {}
	}
	
	/**************************************************************************
	Main function for a talk. Always block on the "message queue" to see whether
	there is a new request. If there is one, consume it by pass it to response
	Executer.
	**************************************************************************/
	@Override
	public void run() {
		// TODO Auto-generated method stub
		JSONObject respJSON;
			
		while (this.isRun) {
			reqType req = getRequest();
			//clntSocket = req.socket;
			try {
				System.out.println("in talk: " + req.reqJSON.toString());
				//clntOut = new PrintStream(req.socket.getOutputStream());
				responseExecuter(req);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println(client.userName + ": is out");
	}
}
