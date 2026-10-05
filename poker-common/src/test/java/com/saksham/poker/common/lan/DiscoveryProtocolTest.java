package com.saksham.poker.common.lan;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.lan.DiscoveryProtocol.Reply;
import org.junit.jupiter.api.Test;

class DiscoveryProtocolTest {

    @Test
    void aReplyIsReadBackAsItWasWritten() {
        String text = DiscoveryProtocol.reply("192.168.1.20", 8080);

        assertThat(text).isEqualTo("HOLDEM_SERVER 192.168.1.20 8080");
        assertThat(DiscoveryProtocol.parseReply(text)).contains(new Reply("192.168.1.20", 8080));
        assertThat(DiscoveryProtocol.parseReply(text + "\n")).contains(new Reply("192.168.1.20", 8080));
    }

    @Test
    void anythingElseIsNotAReply() {
        assertThat(DiscoveryProtocol.parseReply(null)).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("")).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("HOLDEM_DISCOVER")).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("HOLDEM_SERVER 192.168.1.20")).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("HOLDEM_SERVER 192.168.1.20 port")).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("HOLDEM_SERVER 192.168.1.20 0")).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("HOLDEM_SERVER 192.168.1.20 70000")).isEmpty();
        assertThat(DiscoveryProtocol.parseReply("SOMEONE_ELSE 192.168.1.20 8080")).isEmpty();
    }

    @Test
    void aRequestIsRecognisedWhateverSurroundsIt() {
        assertThat(DiscoveryProtocol.isRequest("HOLDEM_DISCOVER")).isTrue();
        assertThat(DiscoveryProtocol.isRequest(" HOLDEM_DISCOVER\r\n")).isTrue();
        assertThat(DiscoveryProtocol.isRequest("holdem_discover")).isFalse();
        assertThat(DiscoveryProtocol.isRequest(null)).isFalse();
    }
}
