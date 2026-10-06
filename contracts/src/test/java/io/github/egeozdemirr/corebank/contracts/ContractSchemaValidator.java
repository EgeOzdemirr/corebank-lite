package io.github.egeozdemirr.corebank.contracts;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;

/**
 * Validates event JSON against its published schema.
 *
 * <p>Shipped in the contracts test-jar so that every producer can prove, in its own tests, that what it writes to
 * the outbox is exactly what the schema promises to consumers.
 */
public final class ContractSchemaValidator {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaIdResolvers(resolvers -> resolvers.mapPrefix(
                    EventCatalog.SCHEMA_ID_BASE, CLASSPATH_PREFIX + EventCatalog.SCHEMA_CLASSPATH_ROOT)));

    /** Returns the validation errors; an empty list means the JSON conforms to the schema. */
    public List<String> validate(EventCatalog event, String json) {
        Schema schema = registry.getSchema(SchemaLocation.of(event.schemaId()));
        List<Error> errors = schema.validate(json, InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
        return errors.stream().map(Error::toString).toList();
    }
}
