package clientRoom;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientObj {
	private final int TimeOut = 60;
	private final int BlockTime = 60;
	
	public boolean isOnline;
	public boolean isBlocked;
	public int timeRemain;
	public int blockTimeRemain;
	
	public String userName;
	public String password;
	
	Set BlackList = Collections.synchronizedSet(new HashSet<String>());
	private BlockingQueue<reqType> mailBox = new LinkedBlockingQueue<reqType>(Integer.MAX_VALUE);
	
	public void leaveMessage (reqType newReq) {
		try {
			mailBox.put(newReq);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public ClientObj(String name, String pswd) {
		isOnline = false;
		isBlocked = false;
		timeRemain = TimeOut;
		blockTimeRemain = BlockTime;
		userName = name;
		password = pswd;
	}
	
	public boolean isInBlackList(String username) {
		return BlackList.contains(username);
	}
	
	public void addBlackList(String username) {
		BlackList.add(username);
	}
	
	public void removeBlackList(String username) {
		BlackList.remove(username);
	}
	
	public void moveIntoEventQ(ClientTalk talkSession) {
		while (!mailBox.isEmpty()) {
			try {
				reqType remainReq = mailBox.take();
				talkSession.insertRequest(remainReq);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.out.println("move Error");
			}
		}
	}
}
