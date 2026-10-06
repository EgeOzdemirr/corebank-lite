package io.github.egeozdemirr.corebank.account.application.port;

import io.github.egeozdemirr.corebank.account.domain.identity.Iban;

public interface IbanGenerator {

    /** A new, unused IBAN. */
    Iban nextIban();
}
