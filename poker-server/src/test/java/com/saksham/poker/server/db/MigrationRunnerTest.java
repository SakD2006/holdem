package com.saksham.poker.server.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.exception.StorageException;
import org.junit.jupiter.api.Test;

class MigrationRunnerTest {

    @Test
    void aScriptIsSplitIntoStatementsWithoutCommentsOrBlanks() {
        String script = """
                -- a comment
                CREATE TABLE a (
                    id INT  -- not stripped: only whole-line comments are
                );

                  -- another comment
                CREATE INDEX a_id ON a (id);
                """;

        assertThat(MigrationRunner.statements(script)).hasSize(2);
        assertThat(MigrationRunner.statements(script).get(0)).startsWith("CREATE TABLE a").endsWith(")");
        assertThat(MigrationRunner.statements(script).get(1)).isEqualTo("CREATE INDEX a_id ON a (id)");
        assertThat(MigrationRunner.statements("\n-- only a comment\n")).isEmpty();
    }

    @Test
    void theVersionComesFromTheFileName() {
        assertThat(MigrationRunner.versionOf("V1__init.sql")).isEqualTo(1);
        assertThat(MigrationRunner.versionOf("V12__add_stats.sql")).isEqualTo(12);
    }

    @Test
    void aBadlyNamedFileIsRefusedWithTheNameToFix() {
        for (String bad : new String[] {"init.sql", "V1_init.sql", "1__init.sql", "V1__init.txt", "Vx__init.sql"}) {
            assertThatThrownBy(() -> MigrationRunner.versionOf(bad))
                    .isInstanceOf(StorageException.class).hasMessageContaining(bad);
        }
    }
}
