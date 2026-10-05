package com.saksham.poker.client.net;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.lan.DiscoveryProtocol;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerDiscoveryTest {

    /** A stand-in server: answers every question it hears with the given text, until closed. */
    private static Thread answerWith(DatagramSocket socket, String... answers) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[256];
            try {
                while (true) {
                    DatagramPacket question = new DatagramPacket(buffer, buffer.length);
                    socket.receive(question);
                    String asked = new String(question.getData(), 0, question.getLength(), StandardCharsets.UTF_8);
                    if (!DiscoveryProtocol.isRequest(asked)) {
                        continue;
                    }
                    for (String answer : answers) {
                        byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
                        socket.send(new DatagramPacket(bytes, bytes.length, question.getAddress(),
                                question.getPort()));
                    }
                }
            } catch (SocketException e) {
                // closed: the test is over
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Test
    void aServerThatAnswersIsFoundAtTheAddressItGives() throws Exception {
        try (DatagramSocket server = new DatagramSocket()) {
            answerWith(server, DiscoveryProtocol.reply("192.168.1.20", 8080));

            List<ServerAddress> found = ServerDiscovery.search(server.getLocalPort(), 500);

            assertThat(found).containsExactly(new ServerAddress("192.168.1.20", 8080));
        }
    }

    @Test
    void aServerHeardTwiceIsListedOnceAndNoiseIsIgnored() throws Exception {
        try (DatagramSocket server = new DatagramSocket()) {
            answerWith(server, DiscoveryProtocol.reply("192.168.1.20", 9090), "who are you?",
                    DiscoveryProtocol.reply("192.168.1.20", 9090));

            List<ServerAddress> found = ServerDiscovery.search(server.getLocalPort(), 500);

            assertThat(found).containsExactly(new ServerAddress("192.168.1.20", 9090));
        }
    }

    @Test
    void nothingAnsweringGivesAnEmptyListOnceTheWaitIsOver() throws Exception {
        int unused;
        try (DatagramSocket probe = new DatagramSocket()) {
            unused = probe.getLocalPort();
        }
        long started = System.nanoTime();

        assertThat(ServerDiscovery.search(unused, 300)).isEmpty();
        assertThat((System.nanoTime() - started) / 1_000_000).isBetween(250L, 2_000L);
    }
}
