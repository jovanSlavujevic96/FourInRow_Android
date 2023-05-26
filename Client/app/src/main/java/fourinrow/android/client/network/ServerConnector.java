package fourinrow.android.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketAddress;

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

    String processMessage(String in) {
        return "";
    }

//    //Pedja:Bice pozvana kada se pozove publishProgress, ali se izvrsava u okviru UI niti, sto znaci
//    //da u njoj mozemo bezbedno raditi sa UI komponentama, u nasem slucaju sa Spinnerom
//    protected void onProgressUpdate(String... values){
//        super.onProgressUpdate(values);
//        String [] arr = new String[values.length - 1];// necu staviti Users: u niz
//        int i = 0;
//        for (String str : values){
//            //nemoj dodati Users string u korisnike koji su povezani
//            if (!str.startsWith("Users:"))
//                arr[i++] = str;
//        }
//        ArrayAdapter<String> aa = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, arr);
//        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        spnUsers.setAdapter(aa);
//
//    }

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
