# ADR-0001: Event contracts are designed for the transaction monitoring component

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

A transaction monitoring and IT general controls component (`services/aml-monitor/`) will be added to this repository
later. It consumes the core banking events, applies rules (thresholds, velocity, sanctions screening, segregation of
duties) and must also run on its own with synthetic data (`tools/synthetic-generator/`). If the event contracts were
shaped only by today's P1 consumers, adding it later would mean breaking changes or a second, divergent event stream.

## Decision

The event contracts were designed with the transaction monitoring and audit component in mind, which will be added to
the same repository.

1. **A separate `contracts` module** holds versioned JSON Schemas (draft 2020-12) and the DTOs generated from them
   (jsonschema2pojo). It is the only code shared between services and with `aml-monitor`; domain code is never
   shared. `EventCatalog` lists every event with its type, schema version, topic and schema file.
2. **Versioned topics:** `banking.accounts.opened.v1`, `banking.transfers.approved.v1`, `banking.transfers.posted.v1`.
   A breaking change creates a `.v2` schema and topic; the old one keeps running until its consumers have moved.
3. **Common envelope on every event:** `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `correlationId`,
   `actorUserId`. Schemas pin `eventType` and `schemaVersion` with `const` and reject unknown properties.
4. **Fields the monitoring rules need are in the contract from the start:** transfer events carry source and target
   account (id and IBAN), beneficiary name, amount and currency, channel, `makerUserId` and `checkerUserId`.
   `checkerUserId` is nullable: transfers below the approval threshold are approved automatically and have no
   checker. `AccountOpened` carries the opening date.
5. **Amounts are decimal strings with exactly two fraction digits** (`"1500.00"`), so no consumer can parse money as a
   binary floating point number.
6. **Partition key is the account id** (the source account for transfers), so one account's events stay ordered.
7. **Retention of several days** (7 by default) and **no single-consumer assumption:** `aml-monitor` reads with its own
   consumer group and can replay.
8. **Events are written through a transactional outbox** in the same database transaction as the business change.
9. **Data minimisation:** events never carry a TCKN.

Items that belong to later roadmap weeks follow the same reasoning and are recorded here so they are not forgotten:
Keycloak roles with stable exported names (`TRANSFER_CREATE`, `TRANSFER_APPROVE`, `ACCOUNT_ADMIN`, `USER_ADMIN`) in a
committed `realm-export.json`, and auditable endpoints `/audit/verify` and `/ops/reconciliation-runs`.

## Consequences

- Adding `aml-monitor` needs no change to producers: it depends on `contracts` and subscribes to existing topics.
- Producers prove conformance in their own tests: account-service validates the outbox payload against the published
  schema (`ContractSchemaValidator` from the contracts test-jar).
- Contract changes need care: additive, optional fields are fine within a version; anything else needs a new version.
- Generated DTOs are mutable JavaBeans (a jsonschema2pojo limitation); services map them at the edge and never use
  them as domain objects.
