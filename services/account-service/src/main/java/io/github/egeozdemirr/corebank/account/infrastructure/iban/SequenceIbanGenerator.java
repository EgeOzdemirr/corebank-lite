package io.github.egeozdemirr.corebank.account.infrastructure.iban;

import io.github.egeozdemirr.corebank.account.application.port.IbanGenerator;
import io.github.egeozdemirr.corebank.account.domain.identity.Iban;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Account numbers come from a database sequence, so they stay unique across service instances. */
@Component
class SequenceIbanGenerator implements IbanGenerator {

    private static final String NEXT_ACCOUNT_NUMBER = "SELECT nextval('account_number_seq')";

    private final JdbcClient jdbcClient;
    private final IbanProperties properties;

    SequenceIbanGenerator(JdbcClient jdbcClient, IbanProperties properties) {
        this.jdbcClient = jdbcClient;
        this.properties = properties;
    }

    @Override
    public Iban nextIban() {
        long accountNumber = jdbcClient.sql(NEXT_ACCOUNT_NUMBER).query(Long.class).single();
        return Iban.forTurkishAccount(properties.syntheticBankCode(), accountNumber);
    }
}
