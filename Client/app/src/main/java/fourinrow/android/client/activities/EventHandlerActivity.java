package fourinrow.android.client.activities;

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
        runOnUiThread(() -> uiThreadHandleImpl(event));
    }

    final protected void activityTransition(Class<?> nextActivityClass) {
        AppCompatActivity currentActivity = this;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                startActivity(new Intent(currentActivity, nextActivityClass));
            }
        }, 2 * 1000);
    }
    protected abstract void uiThreadHandleImpl(Event event);
}
