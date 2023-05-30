package fourinrow.android.client.activities;

import android.app.AlertDialog;
import android.app.Dialog;
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
        activityTransition(nextActivityClass, 1000);
    }

    final protected void activityTransition(Class<?> nextActivityClass, int delay) {
        AppCompatActivity currentActivity = this;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                startActivity(new Intent(currentActivity, nextActivityClass));
            }
        }, delay);
    }

    protected class ButtonSpecs {
        DialogInterface.OnClickListener listener;
        String text;

        public ButtonSpecs(DialogInterface.OnClickListener listener, String text) {
            this.listener = listener;
            this.text = text;
        }
    }

    final protected AlertDialog alertDisplay(String title,
                                             String message,
                                             ButtonSpecs yesButton,
                                             ButtonSpecs noButton) {

        AlertDialog.Builder db = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info);

        if (yesButton != null) {
            db.setPositiveButton(yesButton.text, yesButton.listener);
        }
        if (noButton != null) {
            db.setNegativeButton(noButton.text, noButton.listener);
        }
        return db.show();
    }
    protected abstract void uiThreadHandleImpl(Event event);
}
