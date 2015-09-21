package clientRoom;

import serverManager.WelcomServer;


/**************************************************************************
This thread scans every second to check the Block List, the Online List,
as well as the login List
**************************************************************************/
public class timeScanner implements Runnable{
	
	long timeout = 60000;
	int interval = 1000;
	
	public timeScanner() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (true) {
			try {
				Thread.sleep(interval);
				WelcomServer.clientRoom.scanTimeOut(timeout);
			} catch (Exception e) {}
		}
	}
	
	
}
