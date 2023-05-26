package fourinrow.android.client.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import fourinrow.android.client.R;
import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;
import fourinrow.android.client.network.ServerConnector;
import fourinrow.android.client.states.State;

public class ConnectActivity extends EventHandlerActivity {

    EditText uiIpAddress;
    EditText uiPort;
    Button uiBtnConnect;

    @Override
    protected void uiThreadHandleImpl(Event event) {
        if (event.getPhase() == Phase.CONNECT) {
            switch(event.getState()) {
                case START:
                    // disable connect button
                    uiBtnConnect.setEnabled(false);

                    // start visualization of pending animation
                    break;
                case FAILURE:
                    // enable connect button once again
                    uiBtnConnect.setEnabled(true);

                    // end visualization of pending animation
                    break;
                case SUCCESS:
                    // disable connect button
                    uiBtnConnect.setEnabled(false);

                    // open another activity window
                    activityTransition(RegisterActivity.class);
                    break;
                default:
                    break;
            }
        } else if (event.getPhase() == Phase.DISCONNECT) {
            // state doesn't matter

            // enable connect button once again
            uiBtnConnect.setEnabled(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        uiIpAddress = findViewById(R.id.uiIpAddressInput);
        uiPort = findViewById(R.id.uiPortInput);
//        uiName = findViewById(R.id.etRegistracija);

        uiBtnConnect = findViewById(R.id.uiConnectBtn);
//        uiBtnRegister = findViewById(R.id.btnRegister);
//        uiBtnPlay = findViewById(R.id.btnPlay);

//        spnUsers = findViewById(R.id.spnUsers);

        uiBtnConnect.setOnClickListener(view -> {
            String ip = uiIpAddress.getText().toString();
            String portStr = uiPort.getText().toString();

            if (ip.contentEquals("") || portStr.contentEquals("")) {
                new Report(null, "Greska: Potrebno je uneti IP adresu i port da bi nastavili konekciju")
                        .report(view);
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                new Report(e, "Greska: Uneti port nije integer")
                        .report(view);
                return;
            }

            ServerConnector
                    .resetServer(new InetSocketAddress(ip, port))
                    .bindActivity(this);

            Executors
                    .newSingleThreadExecutor()
                    .execute(ServerConnector.getServer());
        });

//        uiBtnRegister.setOnClickListener(view -> {
//            String ime = uiName.getText().toString();
//
//            MessageSender.sendMessage(output, ime);
//        });

        //Pedja: dodao dugme za igranje, ono drugo mislim da je za registraciju
//        uiBtnPlay.setOnClickListener(view -> {
//            String playWith = spnUsers.getSelectedItem().toString();
//            //dodaj samo proveru da li je odabran korektan korisnik da se igra sa njim, ne mozes sam sa sobom
//            //ja ovde to ne proveravam samo da vidim da li je uopste stiglo do servera sta sam odabrao
//            //if (!playWith.equals(MainActivity.this.name))
//            MessageSender.sendMessage(output, spnUsers.getSelectedItem().toString());
//        });
    }

    public void openActivityGame() {
        Intent intent = new Intent(this, MainActivityLog.class);

        startActivity(intent);
    }
}



