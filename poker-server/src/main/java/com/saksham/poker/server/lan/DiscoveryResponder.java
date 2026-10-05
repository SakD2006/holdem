package com.saksham.poker.server.lan;

import com.saksham.poker.common.lan.DiscoveryProtocol;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers "Find server" (SPEC §4.6). It waits on a UDP port, on a thread of its own, for a desktop
 * app to broadcast a request, and tells that app the address to connect to.
 */
public final class DiscoveryResponder implements Runnable, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryResponder.class);
    private static final String LOOPBACK = "127.0.0.1";

    private final int udpPort;
    private final int httpPort;
    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running;

    /**
     * @param udpPort the port to listen on for requests
     * @param httpPort the port the game runs on, which is what the answer tells the app
     */
    public DiscoveryResponder(int udpPort, int httpPort) {
        this.udpPort = udpPort;
        this.httpPort = httpPort;
    }

    /**
     * Opens the port and starts answering.
     *
     * @throws SocketException if the port cannot be opened, usually because another server on this
     *     computer already has it
     */
    public synchronized void start() throws SocketException {
        socket = new DatagramSocket(udpPort);
        running = true;
        thread = new Thread(this, "lan-discovery");
        thread.setDaemon(true);
        thread.start();
    }

    /** The port being listened on; useful when it was opened as 0, "any free port". */
    public synchronized int port() {
        return socket == null ? udpPort : socket.getLocalPort();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[256];
        while (running) {
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(request);
                String text = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
                if (!DiscoveryProtocol.isRequest(text)) {
                    continue; // something else on the network using the same port
                }
                String ip = addressFor(request.getAddress(), NetworkInfo.lanAddresses());
                byte[] answer = DiscoveryProtocol.reply(ip, httpPort).getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(answer, answer.length, request.getAddress(), request.getPort()));
                log.debug("Told {} that the server is at {}:{}", request.getAddress().getHostAddress(), ip, httpPort);
            } catch (IOException e) {
                if (running) {
                    log.warn("A \"Find server\" request could not be answered: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Picks which of this computer's addresses to give an app. A computer can be on several networks
     * at once (Wi-Fi and a cable, say), and the app can reach only the address on the network it
     * shares with us: the one that starts the same way as its own.
     *
     * @param requester where the request came from
     * @param ours this computer's local network addresses
     */
    static String addressFor(InetAddress requester, List<String> ours) {
        if (ours.isEmpty()) {
            return LOOPBACK; // not on a network: only an app on this same computer can be asking
        }
        String theirs = requester.getHostAddress();
        String best = ours.get(0);
        int bestShared = -1;
        for (String candidate : ours) {
            int shared = sharedParts(candidate, theirs);
            if (shared > bestShared) {
                best = candidate;
                bestShared = shared;
            }
        }
        return best;
    }

    /** How many of the four numbers of two addresses match, counting from the left. */
    private static int sharedParts(String one, String other) {
        String[] a = one.split("\\.");
        String[] b = other.split("\\.");
        int shared = 0;
        while (shared < a.length && shared < b.length && a[shared].equals(b[shared])) {
            shared++;
        }
        return shared;
    }

    /** Stops answering and waits a moment for the thread to finish. */
    @Override
    public synchronized void close() {
        running = false;
        if (socket != null) {
            socket.close(); // makes the waiting receive() give up
        }
        if (thread != null) {
            try {
                thread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
