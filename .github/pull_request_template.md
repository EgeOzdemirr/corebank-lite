## What and why

<!-- One or two sentences. Link the roadmap item or ADR if there is one. -->

## Checklist

- [ ] **Single responsibility kept:** controllers only translate HTTP, rules live in the domain or service layer, persistence in adapters.
- [ ] **New dependencies sit behind an interface** (port) and arrive through constructor injection.
- [ ] **Names are clear:** no abbreviations, no single-letter names, no magic numbers or strings.
- [ ] **Tests exist** for every new rule (unit) and for persistence or messaging changes (Testcontainers).
- [ ] Money uses `Money`/`BigDecimal` (scale 2, HALF_EVEN); no `double`/`float`.
- [ ] No TCKN or unmasked IBAN in logs, exception messages or events.
- [ ] Event contract changes are backward compatible, or a new `.vN` schema and topic was added.
- [ ] No secrets in code or config; `.env.example` updated if a new variable was added.
- [ ] AI-generated code went through the same review and gates; notable AI mistakes added to `docs/ai-workflow.md`.
