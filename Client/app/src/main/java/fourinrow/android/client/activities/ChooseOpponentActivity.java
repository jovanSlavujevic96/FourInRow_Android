package fourinrow.android.client.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import fourinrow.android.client.R;
import fourinrow.android.client.network.MessageSender;
import fourinrow.android.client.network.ServerConnector;
import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;
import fourinrow.android.client.states.State;

public class ChooseOpponentActivity extends EventHandlerActivity {
    private Spinner uiSpinner;
    private Button uiBtnRequestPlay;
    private Button uiBtnRefresh;
    private TextView uiName;
    private AlertDialog gameOfferDialog = null;

    private void refreshOpponents() {
        uiThreadHandleImpl(new Event(Phase.REFRESH, State.START));

        Map<String, String> req = new HashMap<>();
        req.put("method", "refresh");
        MessageSender.sendMessage(JSONObject.toJSONString(req));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_opponent);

        uiSpinner = findViewById(R.id.uiOpponentSpinner);
        uiBtnRequestPlay = findViewById(R.id.uiRequestPlayBtn);
        uiBtnRefresh = findViewById(R.id.uiRefreshBtn);
        uiName = findViewById(R.id.uiName);

        ServerConnector.getServer().bindActivity(this);

        uiSpinner.setAdapter(null);

        uiBtnRequestPlay.setEnabled(false);
        uiBtnRequestPlay.setOnClickListener(view -> {
            String opponent = uiSpinner.getSelectedItem().toString();
            if (opponent.isEmpty()) {
                return;
            }
            uiThreadHandleImpl(new Event(Phase.REQUEST_PLAY, State.START));

            Map<String, String> req = new HashMap<>();
            req.put("method", "requestPlay");
            req.put("opponent", opponent);
            MessageSender.sendMessage(JSONObject.toJSONString(req));
        });

        uiBtnRefresh.setOnClickListener(view -> refreshOpponents());

        if (ServerConnector.getServer().getName() != null) {
            uiName.setText(ServerConnector.getServer().getName());
        } else {
            uiName.setText(R.string.igrac);
        }

        refreshOpponents();
    }

    @Override
    protected void uiThreadHandleImpl(Event event) {
        if (event.getPhase() == Phase.DISCONNECT) {
            // state doesn't matter

            // disable buttons
            uiBtnRequestPlay.setEnabled(false);
            uiBtnRefresh.setEnabled(false);
            uiSpinner.setAdapter(null);

            // jump back to Connect Activity
            activityTransition(ConnectActivity.class);
        } else if (event.getPhase() == Phase.REFRESH) {
            switch (event.getState()) {
                case START -> {
                    // disable buttons
                    uiBtnRefresh.setEnabled(false);
                    uiBtnRequestPlay.setEnabled(false);
                    uiSpinner.setAdapter(null);

                    // start visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
                }
                case FAILURE -> {
                    // enable buttons
                    uiBtnRefresh.setEnabled(true);
                    uiBtnRequestPlay.setEnabled(true);
                    uiSpinner.setAdapter(null);

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                }
                case SUCCESS -> {
                    // enable buttons
                    uiBtnRefresh.setEnabled(true);
                    uiBtnRequestPlay.setEnabled(true);

                    // fill in spinner
                    if (event.getData() instanceof JSONArray jsonUsers) {
                        List<String> users = new ArrayList<>();
                        for (Object user : jsonUsers) {
                            JSONObject jsonUser = (JSONObject) user;
                            try {
                                users.add(Objects.requireNonNull(jsonUser.get("name")).toString());
                            } catch (NullPointerException jsonE) {
                                new Report(jsonE, "Greska: Problemi prilikom parsiranja primljenih podataka");
                            }
                        }

                        if (users.isEmpty()) {
                            uiSpinner.setAdapter(null);
                            uiBtnRequestPlay.setEnabled(false);
                        } else {
                            //Creating the ArrayAdapter instance having the country list
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, users);
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            //Setting the ArrayAdapter data on the Spinner
                            uiSpinner.setAdapter(adapter);
                        }
                    }

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                }
                default -> {
                }
            }
        } else if (event.getPhase() == Phase.REQUEST_PLAY) {
            switch (event.getState()) {
                case START -> {
                    // disable buttons
                    uiBtnRefresh.setEnabled(false);
                    uiBtnRequestPlay.setEnabled(false);
                    uiSpinner.setClickable(false);

                    // start visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
                }
                case FAILURE -> {
                    // enable buttons
                    uiBtnRefresh.setEnabled(true);
                    uiBtnRequestPlay.setEnabled(true);
                    uiSpinner.setClickable(true);

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                }
                case SUCCESS -> {
                    // enable buttons
                    uiBtnRefresh.setEnabled(false);
                    uiBtnRequestPlay.setEnabled(false);
                    uiSpinner.setClickable(false);

                    // jump to Game Activity
                    activityTransition(GameActivity.class);

                    // end visualization of pending animation
                    findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                }
                default -> {
                }
            }
        } else if (event.getPhase() == Phase.REQUEST_PLAY_OFFER) {
            switch (event.getState()) {
                case HOLD -> {
                    // disable buttons
                    uiBtnRefresh.setEnabled(false);
                    uiBtnRequestPlay.setEnabled(false);
                    uiSpinner.setClickable(false);

                    String opponent;
                    if (event.getData() != null) {
                        opponent = Objects.requireNonNull(event.getData()).toString();
                    } else {
                        // enable buttons
                        uiBtnRefresh.setEnabled(true);
                        uiBtnRequestPlay.setEnabled(true);
                        uiSpinner.setClickable(true);

                        return;
                    }

                    gameOfferDialog = alertDisplay(
                            "ZAHTEV ZA IGRU",
                            "Igrac " + opponent + " Vas poziva na partiju.\r\n" +
                                    "Da li prihvatate ?",
                            (dialog, which) -> {
                                // on Accept
                                Map<String, String> req = new HashMap<>();
                                req.put("method", "requestPlayOffer");
                                req.put("accept", "yes");
                                MessageSender.sendMessage(JSONObject.toJSONString(req));
                            },
                            (dialog, which) -> {
                                // on Refuse
                                Map<String, String> req = new HashMap<>();
                                req.put("method", "requestPlayOffer");
                                req.put("accept", "no");
                                MessageSender.sendMessage(JSONObject.toJSONString(req));

                                // enable buttons
                                uiBtnRefresh.setEnabled(true);
                                uiBtnRequestPlay.setEnabled(true);
                                uiSpinner.setClickable(true);
                            }
                    );
                }
                case FAILURE -> {
                    // opponent has dropped before your decision

                    // enable buttons
                    uiBtnRefresh.setEnabled(true);
                    uiBtnRequestPlay.setEnabled(true);
                    uiSpinner.setClickable(true);

                    // if dialog existed removed it
                    if (gameOfferDialog != null) {
                        gameOfferDialog.dismiss();
                        gameOfferDialog = null;
                    }
                }
                case SUCCESS -> {
                    // disable buttons
                    uiBtnRefresh.setEnabled(false);
                    uiBtnRequestPlay.setEnabled(false);
                    uiSpinner.setClickable(false);

                    // jump to Game Activity
                    activityTransition(GameActivity.class);
                }
                default -> {
                }
            }
        }

    }
}
