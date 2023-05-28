package fourinrow.android.client.network;

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

    String getJsonString(JSONObject json, String key) {
        return Objects.requireNonNull(json.get(key)).toString();
    }

    String processMessage(String in) {
        JSONParser parser = new JSONParser();
        JSONObject jsonIn;
        // parse JSON Object
        try {
            jsonIn = (JSONObject)parser.parse(in);
        } catch (ParseException parsE) {
            activity.handleEvent(
                    new Event (Phase.PARSE, State.FAILURE),
                    new Report(parsE, "Greska: Problemi prilikom parsiranja poruke. Pokusajte ponovo poslati zahtev")
            );
            // no need for reply
            return "";
        }

        String method = getJsonString(jsonIn, "method");
        String message = getJsonString(jsonIn, "message");
        String status = getJsonString(jsonIn, "status");
        if (method.isEmpty() || message.isEmpty() || status.isEmpty()) {
            activity.handleEvent(
                    new Event (Phase.PARSE, State.FAILURE),
                    new Report(
                            null,
                            "Greska: Nepotpun odgovor od strane servera"
                    )
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
            activityMessage = (status.contentEquals("200")) ? String.copyValueOf(message.toCharArray()) : "Greska(" + status + "): " + message;
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
                activityMessage = (state == State.SUCCESS) ? String.copyValueOf(message.toCharArray()) : "Greska(" + status + "): " + message;
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
                state = State.FAILURE; // rejected
            }
            activityMessage = message;
        } else {
            return "";
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
