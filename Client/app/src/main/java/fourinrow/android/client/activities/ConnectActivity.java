package fourinrow.android.client.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import fourinrow.android.client.R;
import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;
import fourinrow.android.client.network.ServerConnector;

public class ConnectActivity extends EventHandlerActivity {

    private EditText uiIpAddress;
    private EditText uiPort;
    private Button uiBtnConnect;

    @Override
    protected void uiThreadHandleImpl(Event event) {
        if (event.getPhase() == Phase.DISCONNECT) {
            // state doesn't matter

            // enable connect button
            uiBtnConnect.setEnabled(true);
        } else if (event.getPhase() == Phase.CONNECT) {
            switch(event.getState()) {
                case START:
                    // disable connect button
                    uiBtnConnect.setEnabled(false);

                    // start visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
                    break;
                case FAILURE:
                    // enable connect button once again
                    uiBtnConnect.setEnabled(true);

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                    break;
                case SUCCESS:
                    // disable connect button
                    uiBtnConnect.setEnabled(false);

                    // open another activity window
                    activityTransition(RegisterActivity.class);

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        uiIpAddress = findViewById(R.id.uiIpAddressInput);
        uiPort = findViewById(R.id.uiPortInput);
        uiBtnConnect = findViewById(R.id.uiConnectBtn);

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
    }
}



