package io.github.egeozdemirr.corebank.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EventCatalogTest {

    @ParameterizedTest
    @EnumSource(EventCatalog.class)
    void topic_isVersionedWithTheSchemaVersion(EventCatalog event) {
        assertThat(event.topic()).matches("^banking\\.[a-z]+\\.[a-z]+\\.v" + event.schemaVersion() + "$");
    }

    @ParameterizedTest
    @EnumSource(EventCatalog.class)
    void schema_isPackagedOnTheClasspath(EventCatalog event) {
        String resource = EventCatalog.SCHEMA_CLASSPATH_ROOT + event.schemaPath();

        assertThat(getClass().getClassLoader().getResource(resource)).isNotNull();
        assertThat(event.schemaId()).isEqualTo(EventCatalog.SCHEMA_ID_BASE + event.schemaPath());
    }

    @ParameterizedTest
    @EnumSource(EventCatalog.class)
    void topic_isUniqueAcrossTheCatalog(EventCatalog event) {
        long sameTopic = Arrays.stream(EventCatalog.values())
                .filter(other -> other.topic().equals(event.topic()))
                .count();

        assertThat(sameTopic).isOne();
    }
}
