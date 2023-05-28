package fourinrow.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Client instance which communicates with client in dedicated thread
 * @author Jovan
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private String name;
    private ClientHandler opponent;
    private boolean busy;
    private BufferedReader br;  // for recv
    private PrintWriter pw;     // for send
    
    public String getName() {
        return name;
    }
    
    public boolean isBusy() {
        return busy;
    }
    
    public void disconnect() {
        try {
            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();
        } catch (IOException e) {
            // doesn't really matter
        }
    }
    
    public boolean sendRequest(String req) {
        if (socket.isConnected()) {
            pw.print(req);
            pw.flush();
            return true;
        }
        return false;
    }
    
    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        name = "";
        busy = false;
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
            
            if (method.equalsIgnoreCase("register")) {
                out.put("method", "register");
                if (name.isEmpty()) {
                    // read username
                    if (in.get("name") == null) {
                        out.put("status", "400");
                        out.put("message", "Korisnicko ime nije poslato");
                    } else {
                        String inName = in.get("name").toString();
                        if (inName.isBlank() || inName.isEmpty()) {
                            out.put("status", "400");
                            out.put("message", "Poslato je prazno korisnicko ime");
                        } else if (!inName.matches("^(?=\\s*\\S).{1,15}$")) {
                            out.put("status", "400");
                            out.put("message", "Uneto ime ne sme imati razmake i vise od 15 karaktera");
                        } else if (Server.getServer().findClinet(inName) != null) {
                            out.put("status", "400");
                            out.put("message", "Korisnik sa imenom " + inName + " vec postoji");
                        } else {
                            name = inName;
                            out.put("status", "200");
                            out.put("message", "Korisnik sa imenom " + name + " je uspesno registrovan");
                        }
                    }
                } else {
                    // should not be possible from UI part
                    out.put("status", "400");
                    out.put("message", "Korisnik je vec registrovan");
                }
            } else if (method.equalsIgnoreCase("refresh")) {
                out.put("method", "refresh");
                if (name.isEmpty()) {
                    out.put("status", "400");
                    out.put("message", "Morate se registrovati pre nego sto nastavite dalje");
                } else {
                    out.put("status", "200");
                    out.put("message", "Korisnik je uspesno preuzeo listu potencijalnih protivnika");

                    JSONArray users = new JSONArray();
                    for (ClientHandler client : Server.getServer().getClients()) {
                        if (client != this && !client.busy) {
                            JSONObject user = new JSONObject();
                            user.put("name", client.name);
                            users.add(user);
                        }
                    }
                    out.put("users", users);
                }
            } else if (method.equalsIgnoreCase("requestPlay")) {
                out.put("method", "requestPlay");
                if (name.isEmpty()) {
                    out.put("status", "400");
                    out.put("message", "Morate se registrovati pre nego sto nastavite dalje");
                } else {
                    // look for opponent
                    String opponentName;
                    if (in.get("opponent") != null) {
                        opponentName = in.get("opponent").toString();
                    } else {
                        out.put("status", "400");
                        out.put("message", "Nije naveden protivnik u zahtevu za igru");
                        return out.toJSONString();
                    }
                    
                    // check the opponent
                    ClientHandler opponentHandler = Server.getServer().findClinet(opponentName);
                    if (opponentHandler == null) {
                        out.put("status", "404");
                        out.put("message", "Korisnik " + opponentName + " nije pronadjen");
                    } else if (opponentHandler.busy) {
                        out.put("status", "400");
                        out.put("message", "Korisnik " + opponentName + " je vec u igri");
                    } else {
                        out.put("status", "201"); // 201 -> created (HOLD as reply)
                        out.put("message", "Zahtev za igru je uspesno poslat korisniku " + opponentName);
                        
                        // make them unavailable for other players, even if they don't agree to play
                        busy = true;
                        opponentHandler.busy = true;
                        opponentHandler.opponent = this;
                        opponent = opponentHandler;

                        // send offer to another player in detached thread
                        new ScheduledThreadPoolExecutor(1)
                                .schedule(
                                        () -> {
                                            JSONObject jsonReqToOpponent = new JSONObject();
                                            jsonReqToOpponent.put("method", "requestPlayOffer");
                                            jsonReqToOpponent.put("message", "Zahtev za igru je poslat od strane korisnika " + name);
                                            jsonReqToOpponent.put("status", "201");
                                            jsonReqToOpponent.put("opponent", name);

                                            opponent.sendRequest(jsonReqToOpponent.toJSONString());
                                        },
                                        1500,
                                        TimeUnit.MILLISECONDS
                                );
                    }

                }
            } else if (method.equalsIgnoreCase("requestPlayOffer")) {
                out.put("method", "requestPlayOffer");
                if (name.isEmpty()) {
                    out.put("status", "400");
                    out.put("message", "Morate se registrovati pre nego sto nastavite dalje");
                } else {
                    // look for method
                    String accept;
                    if (in.get("accept") != null) {
                        accept = in.get("accept").toString();
                    } else {
                        out.put("status", "400");
                        out.put("message", "Nepotpun odgovor na ponudu protivnika");
                        return out.toJSONString();
                    }
                    
                    String replyStatus;
                    JSONObject jsonResToOpponent = new JSONObject();
                    jsonResToOpponent.put("method", "requestPlay");
                    if (accept.equalsIgnoreCase("yes")) {
                        replyStatus = "202"; // 202 -> accepted (PROCEED as reply)
                        jsonResToOpponent.put("message", "Zahtev za igru od strane korisnika " + name + " je prihvacen");
                    } else { // consider it "no"
                        replyStatus = "400"; // 400 -> rejected
                        jsonResToOpponent.put("message", "Zahtev za igru od strane korisnika " + name + " je odbijen");

                        // make them available again
                        opponent.busy = false;
                        opponent.opponent = null;
                        opponent = null;
                        busy = false;
                    }
                    jsonResToOpponent.put("status", replyStatus);
                    
                    out.put("status", replyStatus);
                    out.put("message", "Odgovor je uspesno prosledjen");

                    // send offer to another player in detached thread
                    new ScheduledThreadPoolExecutor(1)
                            .schedule(
                                    () -> {
                                        opponent.sendRequest(jsonResToOpponent.toJSONString());
                                    },
                                    1500,
                                    TimeUnit.MILLISECONDS
                            );
                }

            } else {
                out.put("status", "405");
                out.put("message", "Poslati zahtev je nepoznat ili trenutno nije podrzan");
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
                System.out.println("Client \"" + name + "\" disconnected");
                break;
            }
            if (msg == null) {
                System.out.println("Message rcv for client \"" + name + "\" is null");
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
        disconnect();
        Server.getServer().removeClient(this);
    }
}
