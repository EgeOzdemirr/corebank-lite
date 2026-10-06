package io.github.egeozdemirr.corebank.account.infrastructure.iban;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * IBAN settings.
 *
 * @param syntheticBankCode five-digit bank code placed in generated IBANs. It must not belong to a real bank: the
 *                          default {@code 99999} is outside the range TCMB assigns to Turkish banks (for example
 *                          00010, 00062, 00064), so generated IBANs can never point at a real account.
 */
@Validated
@ConfigurationProperties("corebank.account.iban")
public record IbanProperties(@NotNull @Pattern(regexp = "\\d{5}") String syntheticBankCode) {
}
