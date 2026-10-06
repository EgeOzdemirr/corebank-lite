package io.github.egeozdemirr.corebank.account.domain.account;

/** Who owns an account. The owner decides the account type, so the two can never disagree. */
public sealed interface AccountOwner permits CustomerOwner, InstitutionOwner {

    HolderName holderName();

    AccountType accountType();
}
