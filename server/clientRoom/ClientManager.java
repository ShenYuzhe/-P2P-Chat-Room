package clientRoom;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**************************************************************************
This is the class dealing with interaction among clients as well as job
distribution. It distributed all messages received by the main server to 
corresponding client thread. And whenever a client needs information from
other clients or need to communicate to other client, it should talk to this
class first.

ClientTable stores all valid <username, password>
OnlineTable stores all online <username, ClientTalk> where ClientTalk is
				the runnable Object that deals with corresponding client
LoginTable stores all <user, ClientTalk> who is on the process of login.
	Note: if a client is not logged in within 60 sec, the thread will be
	terminated.
BlockTable stores all <user, block_time_stamp> users will be put into this
table, if the entered invalid password for 3 times.
**************************************************************************/
public class ClientManager {
	private String pathName = "./credentials.txt";
	private Hashtable<String, ClientObj> ClientTable = new Hashtable<String, ClientObj>();
	public Hashtable<String, ClientTalk> OnlineTable = new Hashtable<String, ClientTalk>();
	private Hashtable<String, ClientTalk> LoginTable = new Hashtable<String, ClientTalk>();
	private Hashtable<String, Long> BlockTable = new Hashtable<String, Long>();
	
	public ClientManager() {
		initCredential();
		printClients();
		timeScanner timeoutChecker = new timeScanner();
		Thread timeThread = new Thread(timeoutChecker);
		timeThread.start();
	}
	
	/**************************************************************************
	Scans Online List, Login List, Block List. If Online or login 
	client times out, send requests to kill it. If the block time for a client
	is is over, then remove this client from the block table.
	**************************************************************************/
	public void scanTimeOut(long timeout) throws JSONException {
		long currTime = System.currentTimeMillis();
		for (Iterator iter = OnlineTable.keySet().iterator(); iter.hasNext();) {
			String key = (String)iter.next();
			ClientTalk talk = OnlineTable.get(key);
			if (currTime - talk.getLastBeat() >= timeout) {
				JSONObject timeoutJSON = new JSONObject();
				timeoutJSON.put("type", "timeout");
				reqType timeoutReq = new reqType();
				timeoutReq.reqJSON = timeoutJSON;
				talk.insertRequest(timeoutReq);
			}
		}
		
		for (Iterator iter = LoginTable.keySet().iterator(); iter.hasNext();) {
			String key = (String)iter.next();
			ClientTalk talk = LoginTable.get(key);
			if (currTime - talk.getLastBeat() >= timeout) {
				JSONObject timeoutJSON = new JSONObject();
				timeoutJSON.put("type", "login_timeout");
				reqType timeoutReq = new reqType();
				timeoutReq.reqJSON = timeoutJSON;
				talk.insertRequest(timeoutReq);
			}
		}
		
		for (Iterator iter = BlockTable.keySet().iterator(); iter.hasNext();) {
			String key = (String)iter.next();
			Long blockTime = BlockTable.get(key);
			if (currTime - blockTime >= timeout) {
				this.BlockTable.remove(key);
			}
		}
	}
	
	/**************************************************************************
	This is the most function for server side!!! It is called by a client whenever
	this client want to talk to other client.
	This function first checks whether the client talked to is online, then
	check whether the client talked to is valid, and compose corresponding
	response to the client tries to tallk.
	If a client that is talked to is valid but not online, messageCenter will
	put the message to the that client's mail box.
	**************************************************************************/
	public JSONObject messageCenter(reqType reqPackage) throws JSONException {
		String searchName = "";
		JSONObject respJSON = new JSONObject();
		searchName = reqPackage.reqJSON.getString("toname");
		ClientTalk talk = OnlineTable.get(searchName);
		if (talk != null) {
			respJSON.put("type", "success");
			talk.insertRequest(reqPackage);
			return respJSON;
		}
		
		if (reqPackage.reqJSON.getString("type").equals("forcelogout"))
			return respJSON;
		
		ClientObj client = ClientTable.get(searchName);
		if (client != null) {
			respJSON.put("type", "useroffline");
			respJSON.put("fromname", searchName);
			client.leaveMessage(reqPackage);
			return respJSON;
		}
		respJSON.put("type", "error");
		respJSON.put("reason", "user not exist");
		return respJSON;
	}
	
	/**************************************************************************
	Similar as messageCenter, except that it deal with broadcast
	**************************************************************************/
	public JSONObject broadcast(reqType reqPackage) throws JSONException {
		for (Iterator iter = OnlineTable.keySet().iterator(); iter.hasNext();) {
			String key = (String)iter.next();
			if (!key.equals(reqPackage.reqJSON.getString("fromname"))) {
				ClientTalk talk = OnlineTable.get(key);
				talk.insertRequest(reqPackage);
			}
		}
		JSONObject respJSON = new JSONObject();
		respJSON.put("type", "success");
		return respJSON;
	}
	
	public void printClients() {
		for (Iterator iter = ClientTable.keySet().iterator(); iter.hasNext();) {
			String key = (String)iter.next();
			ClientObj client = ClientTable.get(key);
			System.out.println(key + " " + client.password);
		}
	}
	
	public JSONArray getOnlineList() {
		JSONArray onlineList = new JSONArray();
		for (Iterator iter = OnlineTable.keySet().iterator(); iter.hasNext();) {
			onlineList.put((String)iter.next());
		}
		return onlineList;
	}

	public ClientObj getClientByName(String name) {
		ClientObj client = ClientTable.get(name);
		return client;
	}
	
	public boolean isInBlackList(ClientObj client, String fromname) {
		boolean isBlocked = false;
		if (client.isInBlackList(fromname))
			isBlocked = true;
		return isBlocked;
	}
	
	public boolean isUserOnline(String username) {
		boolean isOnline = false;
		ClientTalk talk = OnlineTable.get(username);
		if (talk != null)
			isOnline = true;
		return isOnline;
	}
	
	public JSONObject getIpbyName(String toname, String fromname) {
		JSONObject respJSON = null;
		ClientTalk talk = OnlineTable.get(toname);
		if (talk == null) {
			try {
				respJSON = new JSONObject();
				respJSON.put("type", "error");
				respJSON.put("reason", "user not online");
			} catch (Exception e) {}
		} else {
			respJSON = talk.getIpAddress(fromname);
		}
		return respJSON;
	}
	
	/**************************************************************************
	Every times a client tries to login for first time, it will start a new
	thread for it, and add to the login table. The key of that table is 
	"client_IP:client_listen_port"
	**************************************************************************/
	private void register(JSONObject JSONreq, Socket clntSock) throws JSONException, IOException {
		String username = JSONreq.getString("username");
		String address = JSONreq.getString("address");
		ClientObj client = ClientTable.get(username);
		JSONObject respJSON;
		PrintStream out = new PrintStream(clntSock.getOutputStream());
		if (client == null) {
			respJSON = new JSONObject();
			respJSON.put("type", "invalid name");
			out.println(respJSON.toString());
			clntSock.close();
			return;
		}
		Long blockTime = this.BlockTable.get(username);
		if (blockTime != null) {
			respJSON = new JSONObject();
			respJSON.put("type", "multiple failure");
			out.println(respJSON.toString());
			clntSock.close();
			return;
		}
		ClientTalk newTalk = new ClientTalk(client, clntSock, address);
		reqType loginReq = new reqType();
		loginReq.reqJSON = JSONreq;
		loginReq.socket = clntSock;
		newTalk.insertRequest(loginReq);
		Thread talkThread = new Thread(newTalk);
		talkThread.start();
		this.LoginTable.put(address, newTalk);
	}
	
	/**************************************************************************
	This functions is deals with a very special case.
	For example client A is disconnected from internet for 61 seconds.
	So it times out during this period. But when A reconnect to the Internet and
	tries to message to Server again, A should be warned that A is already
	logged out
	**************************************************************************/
	void warnLogout(Socket clntSock) {
		JSONObject warnJSON = new JSONObject();
		reqType warnReq = new reqType();
		try {
			warnJSON.put("type", "logout");
			warnJSON.put("reason", "already logout");
			PrintStream out = new PrintStream(clntSock.getOutputStream());
			out.println(warnJSON.toString());
			clntSock.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**************************************************************************
	This function send out all the requests received by Main thread of Server to
	corresponding ClientTalk Objects searched either from online table or 
	loginTable.
	**************************************************************************/
	public void distributeReq(String reqStr, Socket clntSock) throws JSONException, IOException {
		System.out.println(reqStr);
		JSONObject reqJSON = new JSONObject(reqStr);
		JSONObject respJSON = new JSONObject();
		PrintStream clntOut = new PrintStream(clntSock.getOutputStream());
		if (reqJSON.getString("type") == null) {
			respJSON.put("type", "error");
			respJSON.put("reason", "invalid operation");
			clntOut.println(respJSON.toString());
		} else if (reqJSON.getString("type").equals("login")) {
			try {
				String address = reqJSON.getString("address");
				ClientTalk loginTalk = this.LoginTable.get(address);
				if (loginTalk != null) {
					reqType loginReq = new reqType(clntSock, reqJSON);
					loginTalk.insertRequest(loginReq);
				} else
					register(reqJSON, clntSock);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			ClientTalk talk = OnlineTable.get(reqJSON.getString("username"));
			if (talk == null) {
				warnLogout(clntSock);
			} else {
				reqType newReq = new reqType(clntSock, reqJSON);
				talk.insertRequest(newReq);
			}
		}
	}
	
	private void initCredential() {
		try {
			File filename = new File(pathName);
			InputStreamReader reader = new InputStreamReader(
					new FileInputStream(filename));
			BufferedReader bufReader = new BufferedReader(reader);
			
			String line = "";
			while (line != null) {
				line = bufReader.readLine();
				if (line == null)
					break;
				String[] fields = new String[2];
				fields = line.split(" ");
				ClientTable.put(fields[0], new ClientObj(fields[0], fields[1]));
			}
			
			bufReader.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setOnline(String username, ClientTalk newtalk) {
		OnlineTable.put(username, newtalk);
		System.out.println("Inserted");
	}
	
	public void leaveLoginList(String address) {
		this.LoginTable.remove(address);
	}
	
	public void setBlock(String username) {
		
		this.BlockTable.put(username, System.currentTimeMillis());
	}
	
	public void setOffline(String username, String IP, int Port) {
		ClientTalk talk = OnlineTable.get(username);
		JSONObject address = talk.getIpAddress(username);
		try {
			if (address.getString("IP").equals(IP) && address.getInt("Port") == Port)
				OnlineTable.remove(username);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("removed");
	}
	
	public void kickByName(String username) {
		OnlineTable.remove(username);
	}
}
