# AI-assisted development log

For each larger feature: what the AI was given, what it got wrong, and how a test or quality gate caught it.

## Week 1: monorepo, contracts, account-service

- **Given:** the private project guide (P1 scope), the file plan approved by the owner, and the decisions on package
  root, opening deposit, transfer schemas and gitleaks.
- **Wrong:** declared the transfer channel enum as a standalone schema with an explicit `javaType`. jsonschema2pojo
  then treated the class left over in `target/classes` as "already existing" and skipped generating it on
  incremental builds. **Caught by:** running the contracts build three times in a row (it failed every other run);
  fixed by inlining the enum.
- **Wrong:** named the HTTP filter `RequestContextFilter`, which clashes with a bean Spring MVC registers under the same
  name. **Caught by:** the `@WebMvcTest` slice failing to start; renamed to `RequestContextHeaderFilter`.
- **Wrong:** added `errorCode` to validation problem details before the base handler had created the body, so it was
  missing. **Caught by:** `AccountControllerTest` asserting `$.errorCode` on a validation error.
- **Wrong:** asserted on the top-level exception message for a commit-time database error; the trigger message is in
  the root cause. **Caught by:** `LedgerIntegrityIT`.
- **Quality gates that pushed back:** SpotBugs flagged a response record exposing a mutable list (now copied
  defensively); PMD flagged a field that could be final (`Account.status`).
