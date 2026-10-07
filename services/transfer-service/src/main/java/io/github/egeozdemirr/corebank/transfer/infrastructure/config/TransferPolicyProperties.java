package io.github.egeozdemirr.corebank.transfer.infrastructure.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Transfer rules from configuration; checked again by {@code CurrencyPolicy} when the application starts.
 *
 * @param businessZone time zone of the business day that daily limits and approval expiry count in
 * @param currencies   rules per ISO 4217 code; a currency without an entry cannot be transferred
 */
@ConfigurationProperties("corebank.transfer.policy")
public record TransferPolicyProperties(ZoneId businessZone, Map<String, CurrencyLimits> currencies) {

    public TransferPolicyProperties {
        Objects.requireNonNull(businessZone, "businessZone");
        currencies = Map.copyOf(Objects.requireNonNull(currencies, "currencies"));
    }

    /**
     * @param approvalThreshold      a transfer needs a checker if its amount is <strong>strictly greater</strong>
     *                               than this; an amount equal to the threshold is approved automatically
     * @param singleTransactionLimit largest amount of one transfer (inclusive)
     * @param dailyLimit             largest total of one source account per business day (inclusive); every
     *                               transfer counts except FAILED ones
     */
    public record CurrencyLimits(BigDecimal approvalThreshold, BigDecimal singleTransactionLimit,
                                 BigDecimal dailyLimit) {
    }
}
