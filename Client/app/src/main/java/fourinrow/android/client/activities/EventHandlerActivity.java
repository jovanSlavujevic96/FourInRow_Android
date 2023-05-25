package fourinrow.android.client.activities;

import androidx.appcompat.app.AppCompatActivity;

import fourinrow.android.client.Report;

public abstract class EventHandlerActivity extends AppCompatActivity {
    final public void handleEvent(String eventName, String status, Report report) {
        runOnUiThread(() -> {
            if (report != null) {
                report.report(findViewById(android.R.id.content));
            }
            uiThreadHandleImpl(eventName, status);
        });
    }
    protected abstract void uiThreadHandleImpl(String eventName, String status);
}
