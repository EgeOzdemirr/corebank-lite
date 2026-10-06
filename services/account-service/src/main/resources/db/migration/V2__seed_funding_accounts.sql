-- The bank's funding account per supported currency: the contra side of opening deposits. A currency without a
-- funding account is not supported. IBANs use the synthetic bank code 99999 (no real bank has it) and account
-- numbers 1-3, below the start of account_number_seq.
INSERT INTO account (id, iban, account_type, customer_id, holder_name, holder_tckn, currency, balance, status,
                     opened_at, version)
VALUES ('00000000-0000-4000-8000-000000000001', 'TR809999900000000000000001', 'FUNDING', NULL,
        'CoreBank Lite Funding TRY', NULL, 'TRY', 0, 'ACTIVE', TIMESTAMPTZ '2026-01-01 00:00:00+00', 0),
       ('00000000-0000-4000-8000-000000000002', 'TR539999900000000000000002', 'FUNDING', NULL,
        'CoreBank Lite Funding USD', NULL, 'USD', 0, 'ACTIVE', TIMESTAMPTZ '2026-01-01 00:00:00+00', 0),
       ('00000000-0000-4000-8000-000000000003', 'TR269999900000000000000003', 'FUNDING', NULL,
        'CoreBank Lite Funding EUR', NULL, 'EUR', 0, 'ACTIVE', TIMESTAMPTZ '2026-01-01 00:00:00+00', 0);
