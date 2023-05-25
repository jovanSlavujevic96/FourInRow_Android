package fourinrow.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Client instance which communicates with client in dedicated thread
 * @author Jovan
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private String userName;
    private BufferedReader br;  // for recv
    private PrintWriter pw;     // for send
    private Consumer<ClientHandler> logoutMethod; // logout method
    
    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public Socket getSocket() {
        return this.socket;
    }
    
    public void setSocket(Socket socket) {
        this.socket = socket;
    }
    
    public PrintWriter getPw() {
        return pw;
    }
    
    // make MD5 hash from entered string
    private String hash(String inputStr) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(inputStr.getBytes());
            byte[] digest = md.digest();
            return DatatypeConverter.printHexBinary(digest).toUpperCase();
        } catch (NoSuchAlgorithmException ex) {
            // cannot occur -> MD5 exists
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }
    
    public ClientHandler(Socket socket, Consumer<ClientHandler> logoutMethod) throws IOException {
        this.socket = socket;
        this.logoutMethod = logoutMethod;
        userName = "";
        
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        } catch (IOException ex) {
            // just disclaimer
            throw ex;
        }
    }
    
    public String processMessage(String msg) {
        JSONObject out = new JSONObject();
        JSONParser parser = new JSONParser();
		
        try {
            // parse JSON Object
            JSONObject in = (JSONObject)parser.parse(msg);

            // look for method
            String method;
            if (in.get("method") != null) {
                method = in.get("method").toString();
            } else {
                out.put("status", "400");
                out.put("message", "Zahtev nije poslat");
                return out.toJSONString();
            }
            
            if (method != null) {
                
            }
            
            // get username
            userName = "";
            if (in.get("username") != null) {
                userName = in.get("username").toString();
            } else {
                out.put("status", "400");
                out.put("message", "Korisnicko ime nije poslato");
                return out.toJSONString();
            }
        } catch (ParseException ex) {
            out.put("status", "500");
            out.put("message", "Interna serverska greska");
        }
            
        return out.toJSONString();
    }
    
    @Override
    public void run() {
        while (true) {
            String msg;

            // receive
            try {
                msg = this.br.readLine();
            } catch (IOException ex) {
                System.out.println("Client \"" + userName + "\" disconnected");
                break;
            }
            if (msg == null) {
                System.out.println("Message rcv for client \"" + userName + "\" is null");
                break;
            }

            // process
            msg = processMessage(msg);

            // send
            if (!msg.equals("")) {
                pw.println(msg);
            }
        }

        // exit
        try {
            this.socket.close();
        } catch (IOException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        logoutMethod.accept(this);
    }
}
