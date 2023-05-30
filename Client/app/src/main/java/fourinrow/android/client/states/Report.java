package fourinrow.android.client.states;

import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public class Report {
    private Exception exception;
    private String message;

    public Report(Exception exception, String message) {
        this.exception = exception;
        this.message = message;
    }

    public void report(View view) {
        // exception report
        if (exception != null) {
            exception.printStackTrace();
        }

        // message report
        if (message != null && !message.isEmpty()) {
            System.out.println(message);
            Snackbar.make(
                    view,
                    message,
                    Snackbar.LENGTH_SHORT
            ).show();
        }
    }
}
