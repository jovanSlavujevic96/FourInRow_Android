package fourinrow.android.client.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import fourinrow.android.client.R;
import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;
import fourinrow.android.client.network.ServerConnector;

public class RegisterActivity extends EventHandlerActivity {

    EditText uiName;
    Button uiBtnRegister;

    @Override
    protected void uiThreadHandleImpl(Event event) {
        if (event.getPhase() == Phase.DISCONNECT) {
            // disable register button
            uiBtnRegister.setEnabled(false);

            // jump back to Connect Activity
            startActivity(new Intent(this, ConnectActivity.class));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        if (ServerConnector.getServer() != null) {
            ServerConnector.getServer().bindActivity(this);
        } else {
            startActivity(new Intent(this, ConnectActivity.class));
            return;
        }

        uiName = findViewById(R.id.uiNameInput);
        uiBtnRegister = findViewById(R.id.uiRegisterBtn);
        uiBtnRegister.setOnClickListener(view -> {
            String name = uiName.getText().toString();
            if (name.contentEquals("")) {
                new Report(null, "Greska: Potrebno je uneti ime da biste nastavili registraciju")
                        .report(view);
            }
        });
    }

    public void openActivityGame() {
        Intent intent = new Intent(this, MainActivityLog.class);

        startActivity(intent);
    }
}



