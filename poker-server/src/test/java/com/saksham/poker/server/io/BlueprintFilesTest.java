package com.saksham.poker.server.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Expert bots need the trainer's files; without good ones the server simply runs without them. */
class BlueprintFilesTest {

    @Test
    void noFilesNamedMeansNoExpertBots() {
        ServerConfig config = new ServerConfig(new Properties());
        assertThat(config.blueprintAbstraction()).isEmpty();
        assertThat(config.blueprintResolveIterations()).isEqualTo(200);
        assertThat(BlueprintFiles.load(config)).isEmpty();
    }

    @Test
    void filesThatAreMissingOrWrongAreReportedNotFatal(@TempDir Path folder) throws Exception {
        Path notAbstraction = folder.resolve("abstraction.bin");
        Files.writeString(notAbstraction, "this is not an abstraction file");
        Properties properties = new Properties();
        properties.setProperty("blueprint.abstraction", notAbstraction.toString());
        properties.setProperty("blueprint.strategy", folder.resolve("missing.bin").toString());
        properties.setProperty("blueprint.resolve.iterations", "50");
        ServerConfig config = new ServerConfig(properties);
        assertThat(config.blueprintAbstraction()).contains(notAbstraction);
        assertThat(config.blueprintResolveIterations()).isEqualTo(50);
        assertThat(BlueprintFiles.load(config)).isEmpty();
    }
}
