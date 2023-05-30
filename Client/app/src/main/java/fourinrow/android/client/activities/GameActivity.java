package fourinrow.android.client.activities;

import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fourinrow.android.client.R;
import fourinrow.android.client.network.MessageSender;
import fourinrow.android.client.network.ServerConnector;
import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Phase;
import fourinrow.android.client.states.Report;

public class GameActivity extends EventHandlerActivity {

    final private Button[][] buttons = new Button[7][6];
    static final int[][] btnRIds = {
            { R.id.button_0_0, R.id.button_0_1, R.id.button_0_2, R.id.button_0_3, R.id.button_0_4, R.id.button_0_5 },
            { R.id.button_1_0, R.id.button_1_1, R.id.button_1_2, R.id.button_1_3, R.id.button_1_4, R.id.button_1_5 },
            { R.id.button_2_0, R.id.button_2_1, R.id.button_2_2, R.id.button_2_3, R.id.button_2_4, R.id.button_2_5 },
            { R.id.button_3_0, R.id.button_3_1, R.id.button_3_2, R.id.button_3_3, R.id.button_3_4, R.id.button_3_5 },
            { R.id.button_4_0, R.id.button_4_1, R.id.button_4_2, R.id.button_4_3, R.id.button_4_4, R.id.button_4_5 },
            { R.id.button_5_0, R.id.button_5_1, R.id.button_5_2, R.id.button_5_3, R.id.button_5_4, R.id.button_5_5 },
            { R.id.button_6_0, R.id.button_6_1, R.id.button_6_2, R.id.button_6_3, R.id.button_6_4, R.id.button_6_5 },
    };
    final private Map<Button, Pair<Integer, Integer>> buttonCoords = new HashMap<>();
    final private Map<Button, Integer> buttonColors = new HashMap<>();
    private boolean playersTurn;
    private int playersColor;
    private int opponentsColor;
    private int whiteColor;
    private AlertDialog latestDialog = null;

    @Override
    protected void uiThreadHandleImpl(Event event) {
        if (event.getPhase() == Phase.DISCONNECT) {
            // state doesn't matter

            playersTurn = false;

            // jump back to Connect Activity
            activityTransition(ConnectActivity.class);
        } else if (event.getPhase() == Phase.PLAY) {
            switch(event.getState()) {
                case FAILURE -> {
                    // play again because you made some mistake
                    playersTurn = true;

                    // if there has been some dialog -> remove it
                    if (latestDialog != null) {
                        latestDialog.dismiss();
                    }
                }
                case SUCCESS -> {
                    JSONObject opponentMove = (JSONObject)event.getData();

                    // it is safe -> it is checked inside Server Connector
                    int i = (int)opponentMove.get("i");
                    int j = (int)opponentMove.get("j");
                    String result = opponentMove.get("result").toString();

                    // set button which opponent chose to opponent's color
                    buttons[i][j].setBackgroundColor(opponentsColor);
                    buttons[i][j].setClickable(false);
                    buttonColors.put(buttons[i][j], opponentsColor);

                    // if there has been some dialog -> remove it
                    if (latestDialog != null) {
                        latestDialog.dismiss();
                    }

                    if (result.equalsIgnoreCase("open")) {
                        // your turn
                        playersTurn = true;

                        // inform about next turn
                        latestDialog = alertDisplay(
                                "POTEZ",
                                "Ti si na potezu",
                                new ButtonSpecs(null, "U REDU"),
                                null
                        );
                    } else if (result.equalsIgnoreCase("lose")) {
                        // can't play anymore
                        playersTurn = false;

                        // make lose announcement
                        latestDialog = alertDisplay(
                                "PORAZ",
                                "Nazalost, izgubili ste od " + ServerConnector.getServer().getOpponentName(),
                                new ButtonSpecs(
                                        (dialog, which) -> {
                                            // log report
                                            new Report(null, "Povratak na meni za izbor protivnika")
                                                    .report(findViewById(android.R.id.content));

                                            // transition to another window (activity)
                                            activityTransition(ChooseOpponentActivity.class, 5000);
                                        },
                                        "NASTAVI"
                                ),
                                null
                        );
                    } else if (result.equalsIgnoreCase("draw")) {
                        // can't play anymore
                        playersTurn = false;

                        // make draw announcement
                        latestDialog = alertDisplay(
                                "NERESENO",
                                "Odigrali ste nereseno sa " + ServerConnector.getServer().getOpponentName(),
                                new ButtonSpecs(
                                        (dialog, which) -> {
                                            // log report
                                            new Report(null, "Povratak na meni za izbor protivnika")
                                                    .report(findViewById(android.R.id.content));

                                            // transition to another window (activity)
                                            activityTransition(ChooseOpponentActivity.class, 5000);
                                        },
                                        "NASTAVI"
                                ),
                                null
                        );
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        TextView player1Name = findViewById(R.id.player1);
        TextView player2Name = findViewById(R.id.player2);

        whiteColor = ContextCompat.getColor(getApplicationContext(), R.color.white);
        ServerConnector.getServer().bindActivity(this);

        if (ServerConnector.getServer().getPlayFirst()) {
            player1Name.setText(ServerConnector.getServer().getPlayerName() + " (Ti)");
            player2Name.setText(ServerConnector.getServer().getOpponentName());
            playersTurn = true;
            playersColor = ContextCompat.getColor(getApplicationContext(), R.color.red);
            opponentsColor = ContextCompat.getColor(getApplicationContext(), R.color.blue);
        } else {
            player1Name.setText(ServerConnector.getServer().getOpponentName());
            player2Name.setText(ServerConnector.getServer().getPlayerName() + " (Ti)");
            playersTurn = false;
            playersColor = ContextCompat.getColor(getApplicationContext(), R.color.blue);
            opponentsColor = ContextCompat.getColor(getApplicationContext(), R.color.red);
        }

        // iterate through columns
        for(int i = 0; i < 7; i++) {
            int finalI = i;
            // create on click listener common for all buttons from the column
            View.OnClickListener listener = (view) -> {
                // can play only when it is player's turn
                if (!playersTurn) {
                    return;
                }
                Button buttonToBeSet = null;

                // iterate starting from below
                for (int j = 5; j >= 0; j--) {
                    if (buttons[finalI][j].isClickable()) {
                        buttonToBeSet = buttons[finalI][j];
                        break;
                    }
                }
                if (buttonToBeSet == null) {
                    return;
                }
                buttonToBeSet.setClickable(false);
                buttonToBeSet.setBackgroundColor(playersColor);
                buttonColors.put(buttonToBeSet, playersColor);

                // set player's turn to false -> opponent is next to play
                playersTurn = false;

                Pair<Integer, Integer> coords = buttonCoords.get(buttonToBeSet);

                Map<String, String> req = new HashMap<>();
                req.put("method", "play");
                req.put("i", coords.first.toString());
                req.put("j", coords.second.toString());

                if (latestDialog != null) {
                    latestDialog.dismiss();
                }

                // check did the player win
                if (winCheck()) {
                    // send lose result to opponent -> he lost
                    req.put("result", "lose");

                    // make winning announcement
                    latestDialog = alertDisplay(
                            "POBEDA",
                            "CESTITAMO! Pobedili ste " + ServerConnector.getServer().getOpponentName(),
                            new ButtonSpecs(
                                    (dialog, which) -> {
                                        // log report
                                        new Report(null, "Povratak na meni za izbor protivnika")
                                                .report(view);

                                        // transition to another window (activity)
                                        activityTransition(ChooseOpponentActivity.class, 5000);
                                    },
                                    "NASTAVI"
                            ),
                            null
                    );
                }
                // if there's no win there is possibility for draw
                else if (drawCheck()) {
                    // send draw result to opponent -> game ended / no winner
                    req.put("result", "draw");

                    // make draw announcement
                    latestDialog = alertDisplay(
                            "NERESENO",
                            "Odigrali ste nereseno sa " + ServerConnector.getServer().getOpponentName(),
                            new ButtonSpecs(
                                    (dialog, which) -> {
                                        // log report
                                        new Report(null, "Povratak na meni za izbor protivnika")
                                                .report(view);

                                        // transition to another window (activity)
                                        activityTransition(ChooseOpponentActivity.class, 5000);
                                    },
                                    "NASTAVI"
                            ),
                            null
                    );
                } else {
                    // send open result to opponent -> still place to play
                    req.put("result", "open");

                    // inform about end of turn
                    latestDialog = alertDisplay(
                            "POTEZ",
                            ServerConnector.getServer().getOpponentName() + " je na potezu",
                            new ButtonSpecs(null, "U REDU"),
                            null
                    );
                }

                // send request to Server
                MessageSender.sendMessage(JSONObject.toJSONString(req));
            };
            // read the buttons from context and initialize them
            // iterate through rows
            for(int j = 0; j < 6; j++) {
                buttons[i][j] = findViewById(btnRIds[i][j]);
                buttons[i][j].setClickable(true);
                buttons[i][j].setOnClickListener(listener);
                buttonCoords.put(buttons[i][j], new Pair<>(i,j));
                buttonColors.put(buttons[i][j], whiteColor);
            }

            String message;
            if (playersTurn) {
                message = "Ti si na potezu";
            } else {
                message = ServerConnector.getServer().getOpponentName() + " je na potezu";
            }
            if (latestDialog != null) {
                latestDialog.dismiss();
            }
            latestDialog = alertDisplay(
                    "POTEZ",
                    message,
                    new ButtonSpecs(null, "U REDU"),
                    null
            );
        }
    }

    // iStep = [-1, 0, 1]
    // jStep = [-1, 0, 1]
    boolean winCheckForSomeDirection(int i, int j, int iStep, int jStep) {
        int steps = 3;
        for (; steps >= 0; steps--) {
            int I = i + (iStep * steps);
            int J = j + (jStep * steps);
            if (buttons[I][J].isClickable() || buttonColors.get(buttons[I][J]) != playersColor) {
                break;
            }
        }
        // win if it is back to the comparing button
        return (steps + 1 == 0);
    }

    boolean winCheck() {
        // iterate through columns
        for (int i = 0; i < 7; i++) {
            // iterate starting from below
            for (int j = 5; j >= 0; j--) {
                Button comparing = buttons[i][j];

                if (comparing.isClickable()) {
                    // if it is clickable -> it means it's not checked yet
                    continue;
                }

                // check is it possible to go vertically up (1+3 steps)
                if (j - 3 >= 0) {
                    if (winCheckForSomeDirection(i,j,0,-1)) {
                        return true;
                    }
                }

                // check is it possible to go horizontally right (1+3 steps)
                if (i + 3 < 7) {
                    if (winCheckForSomeDirection(i,j,+1,0)) {
                        return true;
                    }
                }

                // check is it possible to go diagonally up-left (1+3 steps)
                if (i - 3 >= 0 && j - 3 >= 0) {
                    if (winCheckForSomeDirection(i,j,-1,-1)) {
                        return true;
                    }
                }

                // check is it possible to go diagonally up-right (1+3 steps)
                if (i + 3 < 7 && j - 3 >= 0) {
                    if (winCheckForSomeDirection(i,j,+1,-1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean drawCheck() {
        // iterate through columns
        for (int i = 0; i < 7; i++) {
            // iterate through rows (column items)
            // iterate from top because it is most likely that the top is still empty
            for (int j = 0; j < 6; j++) {
                if (buttons[i][j].isClickable()) {
                    return false;
                }
            }
        }
        return true;
    }
}