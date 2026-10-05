package com.saksham.poker.client.net;

import com.saksham.poker.common.lan.DiscoveryProtocol;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Find server" (SPEC §4.6): shouts a question to every computer on the local network and collects
 * the servers that answer, so a player need not be told an address to type.
 */
public final class ServerDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ServerDiscovery.class);

    /** How long to wait for answers. A server on the same network answers in a few milliseconds. */
    private static final int WAIT_MS = 1_500;

    private ServerDiscovery() {
    }

    /**
     * Looks for servers without holding up the window: the search runs on a thread of its own and
     * the result arrives later. The list is empty if nothing answered.
     */
    public static CompletableFuture<List<ServerAddress>> find() {
        // A host who changed discovery.port on the server can tell the app with -Dholdem.discovery.port.
        int port = Integer.getInteger("holdem.discovery.port", DiscoveryProtocol.DEFAULT_PORT);
        CompletableFuture<List<ServerAddress>> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                result.complete(search(port, WAIT_MS));
            } catch (IOException | RuntimeException e) {
                result.completeExceptionally(new ApiException(null, "Could not search the network: "
                        + e.getMessage() + ". Type the host computer's address instead."));
            }
        }, "server-discovery");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    /**
     * Asks, then listens until the time is up.
     *
     * @param port the UDP port servers listen on
     * @param waitMs how long to listen for
     * @return the servers that answered, each once, in the order they answered
     * @throws IOException if this computer cannot send on any network at all
     */
    static List<ServerAddress> search(int port, int waitMs) throws IOException {
        Set<ServerAddress> found = new LinkedHashSet<>();
        byte[] question = DiscoveryProtocol.REQUEST.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            int sent = 0;
            for (InetAddress target : targets()) {
                try {
                    socket.send(new DatagramPacket(question, question.length, target, port));
                    sent++;
                } catch (IOException e) {
                    // One network refusing a broadcast must not stop us asking on the others.
                    log.debug("Could not ask {}: {}", target.getHostAddress(), e.getMessage());
                }
            }
            if (sent == 0) {
                throw new IOException("this computer would not send on any network");
            }
            long deadline = System.nanoTime() + waitMs * 1_000_000L;
            byte[] buffer = new byte[256];
            while (true) {
                int left = (int) ((deadline - System.nanoTime()) / 1_000_000L);
                if (left <= 0) {
                    break;
                }
                socket.setSoTimeout(left);
                DatagramPacket answer = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(answer);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String text = new String(answer.getData(), 0, answer.getLength(), StandardCharsets.UTF_8);
                DiscoveryProtocol.parseReply(text)
                        .ifPresent(reply -> found.add(new ServerAddress(reply.ip(), reply.port())));
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * Everywhere worth asking: the broadcast address of each network this computer is on, the
     * catch-all broadcast address, and this computer itself for a host who is also playing.
     */
    private static Set<InetAddress> targets() throws IOException {
        Set<InetAddress> targets = new LinkedHashSet<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    if (address.getBroadcast() != null) {
                        targets.add(address.getBroadcast());
                    }
                }
            }
        } catch (SocketException e) {
            log.debug("Could not list this computer's networks: {}", e.getMessage());
        }
        targets.add(InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
        targets.add(InetAddress.getLoopbackAddress());
        return targets;
    }
}
