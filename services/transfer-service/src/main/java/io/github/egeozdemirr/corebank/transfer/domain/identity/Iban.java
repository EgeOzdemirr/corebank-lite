package io.github.egeozdemirr.corebank.transfer.domain.identity;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidIbanException;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Turkish IBAN: {@code TR} + 2 check digits + 5-digit bank code + 1 reserved digit + 16-digit account number. Check
 * digits follow ISO 13616 (mod 97). transfer-service only validates IBANs; account-service issues them.
 *
 * <p>{@link #toString()} is masked because IBANs are personal data under KVKK and must not appear in logs.
 */
public record Iban(String value) {

    private static final Pattern FORMAT = Pattern.compile("^TR\\d{24}$");
    private static final BigInteger CHECK_MODULUS = BigInteger.valueOf(97);
    private static final int VALID_REMAINDER = 1;
    private static final int COUNTRY_AND_CHECK_DIGITS_LENGTH = 4;
    private static final int VISIBLE_PREFIX_LENGTH = 4;
    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final String MASK = "*";

    public Iban {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches() || !hasValidCheckDigits(value)) {
            throw new InvalidIbanException();
        }
    }

    /** Accepts the printed form as well: spaces are removed and letters upper-cased. */
    public static Iban parse(String printed) {
        Objects.requireNonNull(printed, "printed");
        return new Iban(printed.replace(" ", "").toUpperCase(Locale.ROOT));
    }

    public String masked() {
        int hiddenLength = value.length() - VISIBLE_PREFIX_LENGTH - VISIBLE_SUFFIX_LENGTH;
        return value.substring(0, VISIBLE_PREFIX_LENGTH)
                + MASK.repeat(hiddenLength)
                + value.substring(value.length() - VISIBLE_SUFFIX_LENGTH);
    }

    @Override
    public String toString() {
        return masked();
    }

    private static boolean hasValidCheckDigits(String candidate) {
        String rearranged = candidate.substring(COUNTRY_AND_CHECK_DIGITS_LENGTH)
                + candidate.substring(0, COUNTRY_AND_CHECK_DIGITS_LENGTH);
        return remainder(rearranged) == VALID_REMAINDER;
    }

    /** ISO 13616: letters become two-digit numbers (A = 10 ... Z = 35), then the whole number is taken mod 97. */
    private static int remainder(String alphanumeric) {
        StringBuilder digits = new StringBuilder();
        for (char character : alphanumeric.toCharArray()) {
            digits.append(Character.getNumericValue(character));
        }
        return new BigInteger(digits.toString()).mod(CHECK_MODULUS).intValue();
    }
}
