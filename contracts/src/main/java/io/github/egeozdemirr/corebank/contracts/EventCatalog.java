package io.github.egeozdemirr.corebank.contracts;

/**
 * Single source of truth for every published event: its type, schema version, topic and schema file.
 *
 * <p>Topic names carry the schema version so that a breaking change opens a new topic while consumers of the old
 * one keep running. Producers use the account id as the partition key so that one account's events stay ordered.
 */
public enum EventCatalog {

    ACCOUNT_OPENED_V1("AccountOpened", 1, "banking.accounts.opened.v1", "accounts/account-opened.v1.json"),
    TRANSFER_REQUESTED_V1("TransferRequested", 1, "banking.transfers.requested.v1",
            "transfers/transfer-requested.v1.json"),
    TRANSFER_APPROVED_V1("TransferApproved", 1, "banking.transfers.approved.v1", "transfers/transfer-approved.v1.json"),
    TRANSFER_POSTED_V1("TransferPosted", 1, "banking.transfers.posted.v1", "transfers/transfer-posted.v1.json"),
    TRANSFER_FAILED_V1("TransferFailed", 1, "banking.transfers.failed.v1", "transfers/transfer-failed.v1.json");

    /** Base of every schema {@code $id}; schemas are packaged on the classpath under {@link #SCHEMA_CLASSPATH_ROOT}. */
    public static final String SCHEMA_ID_BASE = "https://egeozdemirr.github.io/corebank-lite/schemas/";
    public static final String SCHEMA_CLASSPATH_ROOT = "schemas/";

    private final String eventType;
    private final int schemaVersion;
    private final String topic;
    private final String schemaPath;

    EventCatalog(String eventType, int schemaVersion, String topic, String schemaPath) {
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.topic = topic;
        this.schemaPath = schemaPath;
    }

    public String eventType() {
        return eventType;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String topic() {
        return topic;
    }

    /** Path of the schema relative to {@link #SCHEMA_CLASSPATH_ROOT}. */
    public String schemaPath() {
        return schemaPath;
    }

    public String schemaId() {
        return SCHEMA_ID_BASE + schemaPath;
    }
}
