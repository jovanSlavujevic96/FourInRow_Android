package fourinrow.android.client.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

import fourinrow.android.client.states.Event;
import fourinrow.android.client.states.Report;

public abstract class EventHandlerActivity extends AppCompatActivity {
    final public void handleEvent(Event event, Report report) {
        if (report != null) {
            report.report(findViewById(android.R.id.content));
        }
        if (event != null) {
            runOnUiThread(() -> uiThreadHandleImpl(event));
        }
    }

    final protected void activityTransition(Class<?> nextActivityClass) {
        AppCompatActivity currentActivity = this;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                startActivity(new Intent(currentActivity, nextActivityClass));
            }
        }, 1000);
    }

    final protected AlertDialog alertDisplay(String title,
                                                                    String message,
                                                                    DialogInterface.OnClickListener yesListener,
                                                                    DialogInterface.OnClickListener noListener) {

        return new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)

                // Specifying a listener allows you to take an action before dismissing the dialog.
                // The dialog is automatically dismissed when a dialog button is clicked.
                .setPositiveButton("DA", yesListener)

                // A null listener allows the button to dismiss the dialog and take no further action.
                .setNegativeButton("NE", noListener)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    protected abstract void uiThreadHandleImpl(Event event);
}
