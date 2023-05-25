package fourinrow.android.client.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import fourinrow.android.client.R;
import fourinrow.android.client.Report;
import fourinrow.android.client.network.ServerConnector;

public class ConnectActivity extends EventHandlerActivity {

    EditText uiIpAddress;
    EditText uiPort;
    //    EditText uiName;
    Button uiBtnConnect;
//    Button uiBtnRegister;
//    Button uiBtnPlay;

    private PrintWriter output;
//    Spinner spnUsers;

    @Override
    protected void uiThreadHandleImpl(String eventName, String status) {
        if (eventName.contentEquals("ServerConnector.open")) {
            if (status.contentEquals("start")) {
                uiBtnConnect.setEnabled(false);
            } else if (status.contentEquals("failure")) {
                uiBtnConnect.setEnabled(true);
            } else if (status.contentEquals("success")) {
                uiBtnConnect.setEnabled(false);
            }
        } else if (eventName.contentEquals("ServerConnector.run")) {
            if (status.contentEquals("failure")) {
                uiBtnConnect.setEnabled(true);
            }
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



