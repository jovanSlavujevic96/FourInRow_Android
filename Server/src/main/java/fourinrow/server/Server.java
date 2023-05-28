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
    private static Server server = null;
    
    public static Server createServer(int port) {
        server = new Server(port);
        return server;
    }

    public static Server getServer() {
        return server;
    }

    private ServerSocket acceptSocket;
    private final int port;
    private final Collection<ClientHandler> clients;

    public ClientHandler findClinet(String name) {
        for (ClientHandler client : clients) {
            if (client.getName().equalsIgnoreCase(name)) {
                return client;
            }
        }
        return null;
    }
    
    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }
    
    public Collection<ClientHandler> getClients() {
        return clients;
    }
    
    private void acceptClients() throws IOException {
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
                client_handler = new ClientHandler(client_socket);
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
            client.disconnect();
        }
        acceptSocket.close();
    }
    
    private Server initServer() throws IOException {
        try {
            this.acceptSocket = new ServerSocket(port);
        } catch (IOException ex) {
            // just disclaimer
            throw ex;
        }
        return this;
    }

    private Server(int port) {
        this.port = port;
        clients = new ArrayList();
    }
    
    public static void main(String[] args) {
        final int port = 8080;
        System.out.println("Running server on port " + port + " ...");
        try {
            Server
                .createServer(port)
                .initServer()
                .acceptClients();
        } catch (IOException ex) {
            System.out.println("Server running failed due to: ");
            System.out.println(ex.toString());
        }
    }
}
