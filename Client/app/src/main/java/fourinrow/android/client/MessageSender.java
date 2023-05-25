package fourinrow.android.client;

import java.io.PrintWriter;
import java.util.concurrent.Executors;

public class MessageSender implements Runnable{
    PrintWriter pw;
    String message;

    public MessageSender(PrintWriter pw, String message) {
        this.pw = pw;
        this.message = message;
    }

    @Override
    public void run() {
        pw.println(message);
    }

    static public void sendMessage(PrintWriter pw, String message) {
        Executors
            .newSingleThreadExecutor()
            .execute(
                new MessageSender(pw, message)
            );
    }
}
