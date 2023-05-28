package fourinrow.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
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
    private String opponentName;
    private boolean busy;
    private BufferedReader br;  // for recv
    private PrintWriter pw;     // for send

    private enum Phase {
        NONE,
        CONNECT,
        DISCONNECT,
        PARSE,
        REGISTER,
        REFRESH,
        REQUEST_PLAY,
        REQUEST_PLAY_OFFER,
    }
    
    static private Phase StringToPhase(String phaseStr) {
        if (phaseStr.equalsIgnoreCase("connect")) {
            return Phase.CONNECT;
        } else if (phaseStr.equalsIgnoreCase("disconnect")) {
            return Phase.DISCONNECT;
        } else if (phaseStr.equalsIgnoreCase("parse")) {
            return Phase.PARSE;
        } else if (phaseStr.equalsIgnoreCase("register")) {
            return Phase.REGISTER;
        } else if (phaseStr.equalsIgnoreCase("refresh")) {
            return Phase.REFRESH;
        } else if (phaseStr.equalsIgnoreCase("requestPlay")) {
            return Phase.REQUEST_PLAY;
        } else if (phaseStr.equalsIgnoreCase("requestPlayOffer")) {
            return Phase.REQUEST_PLAY_OFFER;
        }
        return Phase.NONE;
    }

    static private String PhaseToString(Phase phase) {
        return switch (phase) {
            case CONNECT -> "connect";
            case DISCONNECT -> "disconnect";
            case PARSE -> "parse";
            case REGISTER -> "register";
            case REFRESH -> "refresh";
            case REQUEST_PLAY -> "requestPlay";
            case REQUEST_PLAY_OFFER -> "requestPlayOffer";
            default -> "none";
        };
    }

    private Phase phase;

    public String getName() {
        return name;
    }
    
    public boolean isBusy() {
        return busy;
    }
    
    public boolean isAlive() {
        return socket.isConnected();
    }

    public void disconnect() {
        phase = Phase.DISCONNECT;
        try {
            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();
        } catch (IOException e) {
            // doesn't really matter
        }

        // if opponent exist break the bound
        if (busy) {
            ClientHandler opponent = Server.getServer().findClinet(opponentName);
            if (opponent != null) {
                if (opponent.isAlive()) {
                    JSONObject req = new JSONObject();
                    String opponentPhase = ClientHandler.PhaseToString(opponent.phase);
                    req.put("method", opponentPhase);
                    req.put("status", "400");
                    req.put("message", "Proces je prekinut zbog diskonekcije korisnika " + name);
                    opponent.sendRequest(req.toJSONString());
                }
                opponent.opponentName = "";
                opponent.busy = false;
            }
            opponentName = "";
            busy = false;
        }
    }
    
    public void sendRequest(String req) {
        if (socket.isConnected()) {
            pw.println(req);
        }
    }
    
    public ClientHandler(Socket socket) throws IOException {
        phase = Phase.CONNECT;
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
                            phase = Phase.REGISTER;
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
                    phase = Phase.REFRESH;
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
            }
            // message from player who wants to request play to another player
            else if (method.equalsIgnoreCase("requestPlay")) {
                out.put("method", "requestPlay");
                if (name.isEmpty()) {
                    out.put("status", "400");
                    out.put("message", "Morate se registrovati pre nego sto nastavite dalje");
                } else {
                    // look for opponent
                    if (in.get("opponent") != null) {
                        opponentName = in.get("opponent").toString();
                    } else {
                        out.put("status", "400");
                        out.put("message", "Nije naveden protivnik u zahtevu za igru");
                        return out.toJSONString();
                    }
                    
                    // check the opponent
                    ClientHandler opponentHandler = Server.getServer().findClinet(opponentName);
                    if (opponentHandler == null || !opponentHandler.isAlive()) {
                        out.put("status", "404");
                        out.put("message", "Korisnik " + opponentName + " nije pronadjen");
                        opponentName = "";
                    } else if (opponentHandler.busy) {
                        out.put("status", "400");
                        out.put("message", "Korisnik " + opponentName + " je vec u igri");
                        opponentName = "";
                    } else {
                        phase = Phase.REQUEST_PLAY;
                        out.put("status", "201"); // 201 -> created (HOLD as reply)
                        out.put("message", "Zahtev za igru je uspesno poslat korisniku " + opponentName);
                        
                        // make them unavailable for other players, even if they don't agree to play
                        busy = true;
                        opponentHandler.busy = true;
                        opponentHandler.opponentName = name;
                        opponentName = opponentHandler.name;

                        // send offer to another player in detached thread
                        new Timer("requestPlayOffer")
                                .schedule(
                                        new TimerTask() {
                                            @Override
                                            public void run() {
                                                JSONObject jsonReqToOpponent = new JSONObject();
                                                jsonReqToOpponent.put("method", "requestPlayOffer");
                                                jsonReqToOpponent.put("message", "Zahtev za igru je poslat od strane korisnika " + name);
                                                jsonReqToOpponent.put("status", "201");
                                                jsonReqToOpponent.put("opponent", name);

                                                opponentHandler.phase = Phase.REQUEST_PLAY_OFFER;
                                                opponentHandler.sendRequest(jsonReqToOpponent.toJSONString());
                                            }
                                        }, 1500L // wait for 1.5 sec
                                );
                    }

                }
            }
            // message from player who answers to request play from another player
            else if (method.equalsIgnoreCase("requestPlayOffer")) {
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
                    
                    // player must be set to busy and have the opponent name set from another thread
                    if (!busy || opponentName.isEmpty()) {
                        // clear them both then
                        busy = false;
                        opponentName = "";
                        
                        out.put("status", "400");
                        out.put("message", "Veza sa protivnikom je pukla");
                        return out.toJSONString();
                    }
                    
                    // check the opponent
                    ClientHandler opponentHandler = Server.getServer().findClinet(opponentName);
                    if (opponentHandler == null || !opponentHandler.isAlive()) {
                        out.put("status", "404");
                        out.put("message", "Korisnik " + opponentName + " nije pronadjen");
                        opponentName = "";
                        busy = false;
                    } else if (!opponentHandler.busy) {
                        out.put("status", "400");
                        out.put("message", "Korisnik " + opponentName + " je izasao iz igre");
                        opponentName = "";
                        busy = false;
                    } else {
                        String replyStatus; // common for both players
                        String messageToOpponent;
                        phase = Phase.REQUEST_PLAY_OFFER;
                        if (accept.equalsIgnoreCase("yes")) {
                            replyStatus = "202"; // 202 -> accepted (PROCEED as reply)
                            messageToOpponent = "Zahtev za igru od strane korisnika " + name + " je prihvacen";
                        } else { // consider it "no"
                            replyStatus = "400"; // 400 -> rejected
                            messageToOpponent = "Zahtev za igru od strane korisnika " + name + " je odbijen";

                            // make them available again
                            opponentHandler.busy = false;
                            opponentHandler.opponentName = "";
                            opponentName = "";
                            busy = false;
                        }
                        out.put("status", replyStatus);
                        out.put("message", "Odgovor je uspesno prosledjen");

                        // send reply from offer of another player in detached thread
                        new Timer("requestPlay")
                                .schedule(
                                        new TimerTask() {
                                            @Override
                                            public void run() {
                                                JSONObject jsonResToOpponent = new JSONObject();
                                                jsonResToOpponent.put("method", "requestPlay");
                                                jsonResToOpponent.put("message", messageToOpponent);
                                                jsonResToOpponent.put("status", replyStatus);

                                                opponentHandler.phase = Phase.REQUEST_PLAY;
                                                opponentHandler.sendRequest(jsonResToOpponent.toJSONString());
                                            }
                                        }, 1500L // wait for 1.5 sec
                                );
                    }
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
