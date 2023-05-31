package fourinrow.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Client instance which communicates with client in dedicated thread
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private String name;
    private String opponentName;
    private boolean busy;
    private boolean rematch;
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
        PLAY,
        PLAY_AGAIN
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
        } else if (phaseStr.equalsIgnoreCase("play")) {
            return Phase.PLAY;
        } else if (phaseStr.equalsIgnoreCase("playAgain")) {
            return Phase.PLAY_AGAIN;
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
            case PLAY -> "play";
            case PLAY_AGAIN -> "playAgain";
            default -> "none";
        };
    }
    
    static void executeTimerTask(String timerName, long timeout, Function<Void, Void> f) {
        new Timer(timerName)
                .schedule(
                        new TimerTask() {
                            @Override
                            public void run() {
                                f.apply(null);
                            }
                        }, timeout
                );
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
                    req.put("extraStatus", "playerGone");
                    req.put("message", "Proces je prekinut zbog diskonekcije korisnika " + name);
                    opponent.sendRequest(req.toJSONString());
                }
                opponent.opponentName = "";
                opponent.busy = false;
                opponent.rematch = false;
            }
            opponentName = "";
            busy = false;
            rematch = false;
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
        rematch = false;
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

        JSONObject in;
        try {
            // parse JSON Object
            in = (JSONObject)parser.parse(msg);
        } catch (ParseException ex) {
            out.put("status", "500");
            out.put("message", "Interna serverska greska");
            return out.toJSONString();
        }

        // look for method
        String method;
        if (in.get("method") != null) {
            method = in.get("method").toString();
        } else {
            out.put("status", "400");
            out.put("message", "Zahtev nije poslat");
            return out.toJSONString();
        }

        // turn him back the same method
        out.put("method", method);

        Phase methodToPhase = StringToPhase(method);
        if (methodToPhase == Phase.NONE) {
            out.put("status", "405");
            out.put("message", "Poslati zahtev je nepoznat ili trenutno nije podrzan");
            return out.toJSONString();
        }
        else if (methodToPhase != Phase.REGISTER &&
                 methodToPhase != Phase.CONNECT &&
                 methodToPhase != Phase.DISCONNECT) {
            // name should exist in (almost) any method (phase)
            if (name.isEmpty()) {
                out.put("status", "400");
                out.put("message", "Morate se registrovati pre nego sto nastavite dalje");
                return out.toJSONString();
            }
        }

        switch(methodToPhase) {
            case REGISTER:
                // check is user already registered
                if (!name.isEmpty()) {
                    // should not be possible from UI part
                    out.put("status", "400");
                    out.put("message", "Korisnik je vec registrovan");
                    return out.toJSONString();
                }
                // read username
                if (in.get("name") == null) {
                    out.put("status", "400");
                    out.put("message", "Korisnicko ime nije poslato");
                    return out.toJSONString();
                }

                String inName = in.get("name").toString();
                if (inName.isBlank() || inName.isEmpty()) {
                    // error if passed name is empty/blank
                    out.put("status", "400");
                    out.put("message", "Poslato je prazno korisnicko ime");
                } else if (!inName.matches("^(?=\\s*\\S).{1,15}$")) {
                    // error if name doesn't support format
                    out.put("status", "400");
                    out.put("message", "Uneto ime ne sme imati razmake i vise od 15 karaktera");
                } else if (Server.getServer().findClinet(inName) != null) {
                    // error if client already exist
                    out.put("status", "400");
                    out.put("message", "Korisnik sa imenom " + inName + " vec postoji");
                } else {
                    // success
                    phase = Phase.REGISTER;
                    name = inName;
                    out.put("status", "200");
                    out.put("message", "Korisnik sa imenom " + name + " je uspesno registrovan");
                }
                break;
            case REFRESH: {
                rematch = false;
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
                break;
            }
            // message from player who wants to request play to another player
            case REQUEST_PLAY: {
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
                    ClientHandler.executeTimerTask(
                            "requestPlayOffer",
                            1500L,
                            (none) -> {
                                JSONObject jsonReqToOpponent = new JSONObject();
                                jsonReqToOpponent.put("method", "requestPlayOffer");
                                jsonReqToOpponent.put("message", "Zahtev za igru je poslat od strane korisnika " + name);
                                jsonReqToOpponent.put("status", "201");
                                jsonReqToOpponent.put("opponent", name);

                                opponentHandler.phase = Phase.REQUEST_PLAY_OFFER;
                                opponentHandler.sendRequest(jsonReqToOpponent.toJSONString());
                                return null;
                            }
                    );
                }
                break;
            }
            // message from player who answers to request play from another player
            case REQUEST_PLAY_OFFER: {
                // look for accept field
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
                    phase = Phase.PLAY;
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
                    ClientHandler.executeTimerTask(
                            "requestPlay",
                            1500L,
                            (none) -> {
                                JSONObject jsonResToOpponent = new JSONObject();
                                jsonResToOpponent.put("method", "requestPlay");
                                jsonResToOpponent.put("message", messageToOpponent);
                                jsonResToOpponent.put("status", replyStatus);

                                opponentHandler.phase = Phase.PLAY;
                                opponentHandler.sendRequest(jsonResToOpponent.toJSONString());
                                return null;
                            }
                    );
                }
                break;
            }
            case PLAY: {
                // player must be in game
                if (phase != Phase.PLAY || !busy) {
                    out.put("status", "400");
                    out.put("message", "Niste u igri");
                    return out.toJSONString();
                }
                
                // there must be opponent
                ClientHandler opponentHandler = Server.getServer().findClinet(opponentName);
                if (opponentName.isEmpty() || opponentHandler == null) {
                    out.put("status", "404");
                    out.put("message", "Protivnik nije pronadjen");
                    return out.toJSONString();
                }

                // opponent must be in game, too
                if (opponentHandler.phase != Phase.PLAY || !opponentHandler.busy) {
                    out.put("status", "400");
                    out.put("message", "Protivnik nije u igri");
                    return out.toJSONString();
                }

                // look for mandatory play fields
                String result;
                String iCoord, jCoord;
                try {
                    result = Objects.requireNonNull(in.get("result")).toString();
                    iCoord = Objects.requireNonNull(in.get("i")).toString();
                    jCoord = Objects.requireNonNull(in.get("j")).toString();
                } catch (NullPointerException e) {
                    out.put("status", "400");
                    out.put("message", "Nepotpun odgovor u igri");
                    return out.toJSONString();
                }
                
                if (result.equalsIgnoreCase("draw") || result.equalsIgnoreCase("lose")) {
                    phase = Phase.PLAY_AGAIN;
                    opponentHandler.phase = Phase.PLAY_AGAIN;
                } else {
                    phase = Phase.PLAY;
                    opponentHandler.phase = Phase.PLAY;
                }

                // forward message to another player (opponent)
                ClientHandler.executeTimerTask(
                        "play",
                        500L,
                        (none) -> {
                            JSONObject jsonResToOpponent = new JSONObject();
                            jsonResToOpponent.put("method", "play");
                            jsonResToOpponent.put("result",result);
                            jsonResToOpponent.put("i", iCoord);
                            jsonResToOpponent.put("j", jCoord);
                            jsonResToOpponent.put("status", "200");

                            opponentHandler.sendRequest(jsonResToOpponent.toJSONString());
                            return null;
                        }
                );

                // nothing to reply
                return "";
            }
            case PLAY_AGAIN: {
                // there must be opponent
                ClientHandler opponentHandler = Server.getServer().findClinet(opponentName);
                if (opponentName.isEmpty() || opponentHandler == null) {
                    out.put("status", "404");
                    out.put("message", "Protivnik nije pronadjen");
                    return out.toJSONString();
                }

                // opponent must be in game, too
                if (opponentHandler.phase != Phase.PLAY_AGAIN || !opponentHandler.busy) {
                    out.put("status", "400");
                    out.put("message", "Protivnik nije u procesu revansa");
                    return out.toJSONString();
                }
                
                // look for answer field
                String answer;
                if (in.get("answer") != null) {
                    answer = in.get("answer").toString();
                } else {
                    out.put("status", "400");
                    out.put("message", "Nepotpun odgovor na ponudu za revans");
                    return out.toJSONString();
                }

                JSONObject resToOpponent = new JSONObject();
                resToOpponent.put("method", "playAgain");

                if (answer.equalsIgnoreCase("yes")) {
                    rematch = true;

                    // if rematch is true proceed to rematch
                    if (opponentHandler.rematch) {
                        out.put("status", "202");
                        out.put("message", "Protivnik je potvrdio revans");

                        phase = Phase.PLAY;
                        opponentHandler.phase = Phase.PLAY;
                        rematch = false;
                        opponentHandler.rematch = false;
                    }
                    // if rematch is false it means we still wait -> because if he rejects replay will be denied immediatley
                    else {
                        out.put("status", "201");
                        out.put("message", "Ceka se protivnikova potvrda");
                    }
                    resToOpponent.put("status", "202");
                    resToOpponent.put("message", "Protivnik je potvrdio revans");
                } else {
                    phase = Phase.REFRESH;
                    opponentHandler.phase = Phase.REFRESH;
                    rematch = false;
                    opponentHandler.rematch = false;
                    busy = false;
                    opponentHandler.busy = false;
                    
                    out.clear();

                    resToOpponent.put("status", "400");
                    resToOpponent.put("message", "Protivnik je odbio revans");
                }

                // send notification to opponent from player's decision
                ClientHandler.executeTimerTask(
                        "playAgain",
                        1000L,
                        (none) -> {
                            opponentHandler.sendRequest(resToOpponent.toJSONString());
                            return null;
                        }
                );
                break;
            }
        }
        return out.isEmpty() ? "" : out.toJSONString();
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
