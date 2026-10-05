package com.saksham.poker.server.lan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.lan.DiscoveryProtocol;
import com.saksham.poker.common.lan.DiscoveryProtocol.Reply;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryResponderTest {

    private static String ask(DatagramSocket socket, int port, String text) throws Exception {
        byte[] request = text.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(request, request.length, InetAddress.getLoopbackAddress(), port));
        DatagramPacket answer = new DatagramPacket(new byte[256], 256);
        socket.receive(answer);
        return new String(answer.getData(), 0, answer.getLength(), StandardCharsets.UTF_8);
    }

    @Test
    void aRequestIsAnsweredWithTheGamePort() throws Exception {
        try (DiscoveryResponder responder = new DiscoveryResponder(0, 8080);
                DatagramSocket socket = new DatagramSocket()) {
            responder.start();
            socket.setSoTimeout(2_000);

            Reply reply = DiscoveryProtocol.parseReply(ask(socket, responder.port(), DiscoveryProtocol.REQUEST))
                    .orElseThrow();

            assertThat(reply.port()).isEqualTo(8080);
            assertThat(reply.ip()).matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
        }
    }

    @Test
    void anythingElseSentToThePortGetsNoAnswer() throws Exception {
        try (DiscoveryResponder responder = new DiscoveryResponder(0, 8080);
                DatagramSocket socket = new DatagramSocket()) {
            responder.start();
            socket.setSoTimeout(300);

            assertThatThrownBy(() -> ask(socket, responder.port(), "hello?"))
                    .isInstanceOf(SocketTimeoutException.class);
            // ...and it is still listening afterwards.
            socket.setSoTimeout(2_000);
            assertThat(ask(socket, responder.port(), DiscoveryProtocol.REQUEST)).startsWith("HOLDEM_SERVER ");
        }
    }

    @Test
    void closingStopsTheThreadAndFreesThePort() throws Exception {
        DiscoveryResponder responder = new DiscoveryResponder(0, 8080);
        responder.start();
        int port = responder.port();

        responder.close();

        try (DatagramSocket again = new DatagramSocket(port)) {
            assertThat(again.isBound()).isTrue();
        }
    }

    @Test
    void anAppIsGivenTheAddressOnTheNetworkItShares() throws Exception {
        List<String> ours = List.of("10.0.0.5", "192.168.1.20");

        assertThat(DiscoveryResponder.addressFor(InetAddress.getByName("192.168.1.77"), ours))
                .isEqualTo("192.168.1.20");
        assertThat(DiscoveryResponder.addressFor(InetAddress.getByName("10.0.0.9"), ours)).isEqualTo("10.0.0.5");
        // An app on this same computer is given an address the other players could use too.
        assertThat(DiscoveryResponder.addressFor(InetAddress.getLoopbackAddress(), ours)).isEqualTo("10.0.0.5");
        assertThat(DiscoveryResponder.addressFor(InetAddress.getLoopbackAddress(), List.of())).isEqualTo("127.0.0.1");
    }
}
