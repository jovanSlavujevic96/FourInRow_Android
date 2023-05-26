package fourinrow.android.client.network;

import java.io.PrintWriter;
import java.util.concurrent.Executors;

public class MessageSender implements Runnable{
    PrintWriter pw;
    String message;

    private MessageSender(PrintWriter pw, String message) {
        this.pw = pw;
        this.message = message;
    }

    @Override
    public void run() {
        pw.println(message);
    }

    static public void sendMessage(String message) {
        PrintWriter pw = ServerConnector.getServer().getPw();
        if (pw != null) {
            Executors
                    .newSingleThreadExecutor()
                    .execute(new MessageSender(pw, message));
        }
    }
}
