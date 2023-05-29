package fourinrow.android.client.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fourinrow.android.client.R;
import fourinrow.android.client.network.MessageSender;
import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;
import fourinrow.android.client.network.ServerConnector;
import fourinrow.android.client.states.State;

public class RegisterActivity extends EventHandlerActivity {

    private EditText uiName;
    private Button uiBtnRegister;

    @Override
    protected void uiThreadHandleImpl(Event event) {
        if (event.getPhase() == Phase.DISCONNECT) {
            // state doesn't matter

            // disable register button
            uiBtnRegister.setEnabled(false);

            // jump back to Connect Activity
            activityTransition(ConnectActivity.class);
        } else if (event.getPhase() == Phase.PARSE) {
            if (event.getState() == State.FAILURE) {
                // enable connect button once again
                uiBtnRegister.setEnabled(true);

                // end visualization of pending animation
                findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
            }
        } else if(event.getPhase() == Phase.REGISTER)   {
            switch (event.getState()) {
                case START -> {
                    // disable connect button
                    uiBtnRegister.setEnabled(false);

                    // start visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
                }
                case FAILURE -> {
                    // enable connect button once again
                    uiBtnRegister.setEnabled(true);

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                }
                case SUCCESS -> {
                    // disable connect button
                    uiBtnRegister.setEnabled(false);

                    // open another activity window
                    activityTransition(ChooseOpponentActivity.class);

                    // set name to be known among the code
                    ServerConnector.getServer().setName(uiName.getText().toString());

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                }
                default -> {
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        if (ServerConnector.getServer() != null) {
            ServerConnector.getServer().bindActivity(this);
        } else {
            // jump back to Connect Activity
            activityTransition(ConnectActivity.class);
            return;
        }

        uiName = findViewById(R.id.uiNameInput);
        uiBtnRegister = findViewById(R.id.uiRegisterBtn);
        uiBtnRegister.setOnClickListener(view -> {
            String name = uiName.getText().toString();
            if (name.isEmpty() || name.isBlank()) {
                new Report(null, "Greska: Potrebno je uneti ime da biste nastavili registraciju")
                        .report(view);
            } else if (!name.matches("^(?=\\s*\\S).{1,15}$")) {
                new Report(null, "Greska: Uneto ime ne sme imati razmake i vise od 15 karaktera")
                        .report(view);
            } else {
                Map<String, String> req = new HashMap<>();
                req.put("method", "register");
                req.put("name", name);

                uiThreadHandleImpl(new Event(Phase.REGISTER, State.START));
                MessageSender.sendMessage(JSONObject.toJSONString(req));
            }
        });
    }
}



