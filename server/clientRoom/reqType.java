package clientRoom;

import org.json.JSONObject;
import java.net.Socket;


/**************************************************************************
This class is the type for every request.
It consists of a JSON carrying all the information needed and a socket
which might be used if this request needs immediately response
**************************************************************************/
public class reqType {
	public Socket socket;
	public JSONObject reqJSON;
	public reqType(Socket newSocket, JSONObject newReqJSON) {
		// TODO Auto-generated constructor stub
		socket = newSocket;
		reqJSON = newReqJSON;
	}
	
	public reqType() {
		// TODO Auto-generated constructor stub
		socket = null;
		reqJSON = null;
	}
}
