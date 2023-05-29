package fourinrow.android.client.activities;

import androidx.core.content.ContextCompat;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import fourinrow.android.client.R;
import fourinrow.android.client.network.ServerConnector;
import fourinrow.android.client.states.Event;

public class GameActivity extends EventHandlerActivity {

    final private Button[][] buttons = new Button[7][6];
    final private Map<Button, Boolean> buttonsUsed = new HashMap<>();
    static final int[][] btnRIds = {
            { R.id.button_0_0, R.id.button_0_1, R.id.button_0_2, R.id.button_0_3, R.id.button_0_4, R.id.button_0_5 },
            { R.id.button_1_0, R.id.button_1_1, R.id.button_1_2, R.id.button_1_3, R.id.button_1_4, R.id.button_1_5 },
            { R.id.button_2_0, R.id.button_2_1, R.id.button_2_2, R.id.button_2_3, R.id.button_2_4, R.id.button_2_5 },
            { R.id.button_3_0, R.id.button_3_1, R.id.button_3_2, R.id.button_3_3, R.id.button_3_4, R.id.button_3_5 },
            { R.id.button_4_0, R.id.button_4_1, R.id.button_4_2, R.id.button_4_3, R.id.button_4_4, R.id.button_4_5 },
            { R.id.button_5_0, R.id.button_5_1, R.id.button_5_2, R.id.button_5_3, R.id.button_5_4, R.id.button_5_5 },
            { R.id.button_6_0, R.id.button_6_1, R.id.button_6_2, R.id.button_6_3, R.id.button_6_4, R.id.button_6_5 },
    };

    @Override
    protected void uiThreadHandleImpl(Event event) {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        TextView player1Name = findViewById(R.id.player1);
        TextView player2Name = findViewById(R.id.player2);

        ServerConnector.getServer().bindActivity(this);

        if (ServerConnector.getServer().getPlayFirst()) {
            player1Name.setText(ServerConnector.getServer().getPlayerName());
            player2Name.setText(ServerConnector.getServer().getOpponentName());
        } else {
            player1Name.setText(ServerConnector.getServer().getOpponentName());
            player2Name.setText(ServerConnector.getServer().getPlayerName());
        }

        for(int i = 0; i < 7; i++) {
            int finalI = i;
            View.OnClickListener listener = (view) -> {
                Button buttonToBeSet = null;
                for (int j = 5; j >= 0; j--) {
                    if (buttons[finalI][j].isClickable()) {
                        buttonToBeSet = buttons[finalI][j];
                        break;
                    }
                }
                if (buttonToBeSet != null) {
                    buttonToBeSet.setClickable(false);
                    buttonToBeSet.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.red));
                }
            };
            for(int j = 0; j < 6; j++) {
                buttons[i][j] = findViewById(btnRIds[i][j]);
                buttons[i][j].setClickable(true);
                buttons[i][j].setOnClickListener(listener);
                buttonsUsed.put(buttons[i][j], false);
            }
        }
    }

}