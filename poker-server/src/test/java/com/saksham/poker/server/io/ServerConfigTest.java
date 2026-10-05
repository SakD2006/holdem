package com.saksham.poker.server.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigTest {

    @Test
    void defaultsMatchTheDockerDatabase() {
        ServerConfig config = new ServerConfig(new Properties());

        assertThat(config.dbUrl()).isEqualTo("jdbc:postgresql://localhost:5433/holdem");
        assertThat(config.dbUser()).isEqualTo("holdem");
        assertThat(config.dbPassword()).isEqualTo("holdem");
        assertThat(config.dbPoolSize()).isEqualTo(5);
        assertThat(config.httpPort()).isEqualTo(8080);
        assertThat(config.discoveryPort()).isEqualTo(8888);
        assertThat(config.tokenLifetime()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void aMissingFileGivesTheDefaults(@TempDir Path folder) {
        ServerConfig config = ServerConfig.load(folder.resolve("server.properties"));

        assertThat(config.httpPort()).isEqualTo(8080);
    }

    @Test
    void settingsInTheFileReplaceTheDefaultsAndTheRestStay(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("server.properties");
        Files.writeString(file, """
                # comment
                db.url = jdbc:postgresql://db.local:5432/poker
                db.user=asha
                db.password=s3cret
                http.port = 9090
                token.days=1
                """);

        ServerConfig config = ServerConfig.load(file);

        assertThat(config.dbUrl()).isEqualTo("jdbc:postgresql://db.local:5432/poker");
        assertThat(config.dbUser()).isEqualTo("asha");
        assertThat(config.dbPassword()).isEqualTo("s3cret");
        assertThat(config.httpPort()).isEqualTo(9090);
        assertThat(config.tokenLifetime()).isEqualTo(Duration.ofDays(1));
        assertThat(config.discoveryPort()).isEqualTo(8888);
    }

    @Test
    void theExampleFileInTheRepositoryIsValid() {
        ServerConfig config = ServerConfig.load(Path.of("..", "server.properties.example"));

        assertThat(config.dbUrl()).startsWith("jdbc:postgresql://");
    }

    @Test
    void aNumberThatIsNotANumberOrIsOutOfRangeIsRefusedByName() {
        Properties text = new Properties();
        text.setProperty("http.port", "eighty");
        Properties tooBig = new Properties();
        tooBig.setProperty("http.port", "70000");

        assertThatIllegalArgumentException().isThrownBy(() -> new ServerConfig(text))
                .withMessageContaining("http.port").withMessageContaining("eighty");
        assertThatIllegalArgumentException().isThrownBy(() -> new ServerConfig(tooBig))
                .withMessageContaining("http.port").withMessageContaining("70000");
    }
}
