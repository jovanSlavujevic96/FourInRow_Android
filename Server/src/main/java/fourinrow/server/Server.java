package fourinrow.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jovan
 */
public class Server {
    private ServerSocket acceptSocket;
    private final Collection<ClientHandler> clients;
    
    public void acceptClients() throws IOException {
        Socket client_socket;
        Thread client_thread;
        ClientHandler client_handler;

        while (true) {
            // wait for new client
            System.out.println("Waiting for new clients..");
            try {
                client_socket = this.acceptSocket.accept();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                break;
            }
            if (client_socket == null) {
                System.out.println("Accept client returned null socket");
                break;
            }

            // create new client handler for connected client
            // logout method is actually removing created client handler from list
            try {
                client_handler = new ClientHandler(client_socket, (n) -> { clients.remove(n); });
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                break;
            }
            System.out.println("Accepted new client [" + client_socket.getInetAddress().getHostAddress() + ":" + client_socket.getPort() + "]");

            // add client handler to list, create and start thread
            clients.add(client_handler);
            client_thread = new Thread(client_handler);
            client_thread.start();
        }

        // safe exit
        for (ClientHandler client : clients) {
            client.getSocket().shutdownOutput();
            client.getSocket().shutdownInput();
            client.getSocket().close();
        }
        acceptSocket.close();
    }
    
    public Server(int port) throws IOException {
        try {
            this.acceptSocket = new ServerSocket(port);
        } catch (IOException ex) {
            // just disclaimer
            throw ex;
        }
        clients = new ArrayList();
    }
    
    public static void main(String[] args) {
        Server server;
        
        final int port = 8080;
        System.out.println("Server port is " + port);
        
        try {
            server = new Server(port);
            server.acceptClients();

        } catch (IOException ex) {
            System.out.println("StickerServer create failed: " + ex.toString());
        }
    }
}
