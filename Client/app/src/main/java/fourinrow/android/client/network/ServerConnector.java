package fourinrow.android.client.network;

import androidx.annotation.NonNull;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;

import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;
import fourinrow.android.client.activities.EventHandlerActivity;
import fourinrow.android.client.states.State;

public class ServerConnector implements Runnable {
    final private SocketAddress serverAddress;
    private Socket socket;
    private BufferedReader br;
    private PrintWriter pw;
    private EventHandlerActivity activity = null;

    private String playerName = ""; // player name
    private String opponentName = ""; // opponent name
    private boolean playFirst;

    static private ServerConnector server = null;

    static public ServerConnector resetServer(SocketAddress address) {
        server = new ServerConnector(address);
        return server;
    }

    static public ServerConnector getServer() {
        return server;
    }

    private ServerConnector(SocketAddress address) {
        serverAddress = address;
    }

    public void bindActivity(EventHandlerActivity activity) {
        this.activity = activity;
    }

    public void setPlayerName(String name) { playerName = name; }
    public String getPlayerName() { return playerName; }

    public void setOpponentName(String name) { opponentName = name; }
    public String getOpponentName() { return opponentName; }

    public void setPlayFirst(boolean first) { playFirst = first; }
    public boolean getPlayFirst() { return playFirst; }

    PrintWriter getPw() { return pw; }

    void open() throws RuntimeException {

        // inform MainActivity about start of open process
        activity.handleEvent(new Event(Phase.CONNECT, State.START), null);

        socket = new Socket();
        try {
            // try to connect and wait for 2 sec
            socket.connect(serverAddress, 2000);
        } catch (IOException connectE) {
            // inform main activity about negative outcome of process
            activity.handleEvent(
                    new Event (Phase.CONNECT, State.FAILURE),
                    new Report(
                            connectE,
                            "Greska: Neuspesna konekcija sa serverom. Pokusajte ponovo konekciju"
                    )
            );

            // throw exception so it can be caught in run method and closed
            throw new RuntimeException();
        }

        // extract br & pw from socket
        try {
            pw = new PrintWriter(socket.getOutputStream(), true);
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException extractE) {
            // failed to extract br & pw

            // close resources
            close();

            // inform main activity about negative outcome of process
            activity.handleEvent(
                    new Event (Phase.CONNECT, State.FAILURE),
                    new Report(
                            extractE,
                            "Greska: Neuspesno mrezno podesavanje. Pokusajte ponovo konekciju"
                    )
            );

            // throw exception so it can be caught in run method and closed
            throw new RuntimeException();
        }

        // inform main activity about positive outcome of process
        activity.handleEvent(
                new Event (Phase.CONNECT, State.SUCCESS),
                new Report(
                        null,
                        "Uspesno povezivanje sa serverom"
                )
        );
    }

    void close() {
        // close opened socket
        try {
            socket.close();
            socket.shutdownInput();
            socket.shutdownOutput();
        } catch (IOException closeE) {
            // this exception doesn't really matter
        }

        // reset properties
        socket = null;
        br = null;
        pw = null;
    }

    String getJsonString(@NonNull JSONObject json, String key) {
        try {
            return Objects.requireNonNull(json.get(key)).toString();
        } catch (NullPointerException e) {
            return "";
        }
    }

    String processMessage(String in) {
        JSONParser parser = new JSONParser();
        JSONObject jsonIn;
        // parse JSON Object
        try {
            jsonIn = (JSONObject)parser.parse(in);
        } catch (ParseException parsE) {
            activity.handleEvent(
                    new Event(Phase.PARSE, State.FAILURE),
                    new Report(parsE, "Greska: Problemi prilikom parsiranja poruke. Pokusajte ponovo poslati zahtev")
            );
            // no need for reply
            return "";
        }

        String message = getJsonString(jsonIn, "message");
        String status = getJsonString(jsonIn, "status");
        String method = getJsonString(jsonIn, "method");

        if (status.isEmpty() || message.isEmpty()) {
            if (!method.equalsIgnoreCase("play")) {
                activity.handleEvent(
                        new Event(Phase.PARSE, State.FAILURE),
                        new Report(null,"Greska: Fale mandatorni delovi u dogovoru")
                );
                // no need for reply
                return "";
            }
        } else if (method.isEmpty() && status.charAt(0) == '2') {
            // if status is OK method should exist
            activity.handleEvent(
                    new Event(Phase.PARSE, State.FAILURE),
                    new Report(null,"Greska: Nije poznato na koji zahtev je server odgovorio")
            );
            // no need for reply
            return "";
        }

        State state;
        String activityMessage = "";
        Phase phase;
        String reply = "";
        Object data = null;

        if (method.equalsIgnoreCase("register")) {
            phase = Phase.REGISTER;
            state = (status.contentEquals("200")) ? State.SUCCESS : State.FAILURE;
            activityMessage = (status.contentEquals("200")) ?
                    String.copyValueOf(message.toCharArray()) :
                    "Greska(" + status + "): " + message;
        } else if (method.equalsIgnoreCase("refresh")) {
            phase = Phase.REFRESH;
            state = (status.contentEquals("200")) ? State.SUCCESS : State.FAILURE;

            if (state == State.SUCCESS) {
                if (jsonIn.get("users") instanceof JSONArray jsonUsers) {
                    if (jsonUsers.isEmpty()) {
                        state = State.FAILURE;
                        activityMessage = "Nazalost trenutno nema potencijalnih protivnika za igru";
                    } else {
                        data = jsonUsers;
                        // activity message in if below
                    }
                } else {
                    state = State.FAILURE;
                    activityMessage = "Greska: Nisu poslati korisnici";
                }
            }

            if (activityMessage.isEmpty()) { // handles two condition variants
                activityMessage = (state == State.SUCCESS) ?
                        String.copyValueOf(message.toCharArray()) :
                        "Greska(" + status + "): " + message;
            }
        } else if (method.equalsIgnoreCase("requestPlay")) {
            // reply from your request to another player
            phase = Phase.REQUEST_PLAY;

            if (status.contentEquals("201")) {
                state = State.HOLD; // just infomation from server that req to opponent has been sent
            } else if (status.contentEquals("202")) {
                state = State.SUCCESS; // accepted
            } else {
                state = State.FAILURE; // rejected
            }
            activityMessage = message;
        } else if (method.equalsIgnoreCase("requestPlayOffer")) {
            // got offer from another player
            phase = Phase.REQUEST_PLAY_OFFER;

            if (status.contentEquals("201")) {
                data = jsonIn.get("opponent");
                state = State.HOLD; // just infomation from server that req to opponent has been sent
            } else if (status.contentEquals("202")) {
                state = State.SUCCESS; // accepted
            } else {
                state = State.FAILURE; // rejected or some other occurrence
            }
            activityMessage = message;
        } else if(method.equalsIgnoreCase("play")) {
            phase = Phase.PLAY;
            state = (status.equalsIgnoreCase("200")) ? State.SUCCESS : State.FAILURE;
            if (state == State.SUCCESS) {
                // check necessary JSON fields
                try {
                    Integer i = Integer.parseInt(Objects.requireNonNull(jsonIn.get("i")).toString());
                    Integer j = Integer.parseInt(Objects.requireNonNull(jsonIn.get("j")).toString());
                    Objects.requireNonNull(jsonIn.get("result"));

                    // put Integers instead of string
                    jsonIn.put("i", i);
                    jsonIn.put("j", j);
                } catch (NullPointerException nullE) {
                    state = State.FAILURE;
                    activityMessage = "Nedostaju neophodna polja prilikom slanja poteza";
                } catch (NumberFormatException numberE) {
                    state = State.FAILURE;
                    activityMessage = "Poslate koordinate nisu brojevi";
                }

                // remove unnecessary data from JSON
                jsonIn.remove("status");
                jsonIn.remove("method");

                // pass it to game activity
                data = jsonIn;
            }
        } else {
            phase = Phase.NONE;
            state = State.NONE;
        }

        activity.handleEvent(new Event(phase, state, data), new Report(null, activityMessage));
        return reply;
    }

    @Override
    public void run() {
        try {
            // open resources
            open();
        } catch (RuntimeException e) {
            // for exception occurrences just exit
            return;
        }

        // infinite receive/process loop
        while(true){
            String rcvMsg;
            try {
                rcvMsg = br.readLine();
            } catch (IOException recvE) {
                // inform main activity about negative outcome of process
                activity.handleEvent(
                        new Event (Phase.DISCONNECT, State.FAILURE),
                        new Report(
                                recvE,
                                "Greska: Veza sa serverom je pukla"
                        )
                );

                // receive failed -> proceed to exit
                break;
            }

            if (rcvMsg == null) {
                // inform main activity about negative outcome of process
                activity.handleEvent(
                        new Event (Phase.DISCONNECT, State.FAILURE),
                        new Report(
                                null,
                                "Greska: Primljena poruka je prazna"
                        )
                );

                // receive failed -> proceed to exit
                break;
            }

            // process message
            String sndMsg = processMessage(rcvMsg);

            // send necessary response
            if (!sndMsg.contentEquals("")) {
                pw.println(sndMsg);
            }
        }

        // close resources
        close();
    }
}
