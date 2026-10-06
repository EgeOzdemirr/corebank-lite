package io.github.egeozdemirr.corebank.account.domain.identity;

import io.github.egeozdemirr.corebank.account.domain.exception.InvalidTcknException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Turkish national identity number (T.C. Kimlik No): 11 digits, first digit non-zero, last two digits are check
 * digits. Only synthetic values that satisfy the algorithm are used in this project.
 *
 * <p>{@link #toString()} is masked ({@code 123******01}) so that a TCKN never reaches a log line by accident.
 */
public record Tckn(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[1-9]\\d{10}$");
    private static final int FIRST_CHECK_DIGIT_INDEX = 9;
    private static final int SECOND_CHECK_DIGIT_INDEX = 10;
    private static final int ODD_POSITION_WEIGHT = 7;
    private static final int CHECK_MODULUS = 10;
    private static final int POSITION_STEP = 2;
    private static final int VISIBLE_PREFIX_LENGTH = 3;
    private static final int VISIBLE_SUFFIX_LENGTH = 2;
    private static final String MASK = "*";

    public Tckn {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches() || !hasValidCheckDigits(value)) {
            throw new InvalidTcknException();
        }
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

    /**
     * Positions are 1-based in the official definition: digit 10 is (7 x sum of odd positions 1-9 minus sum of even
     * positions 2-8) mod 10, and digit 11 is the sum of the first ten digits mod 10.
     */
    private static boolean hasValidCheckDigits(String candidate) {
        int[] digits = candidate.chars().map(Character::getNumericValue).toArray();
        int oddPositionSum = sumEveryOtherDigit(digits, 0);
        int evenPositionSum = sumEveryOtherDigit(digits, 1);
        int expectedFirst = Math.floorMod(oddPositionSum * ODD_POSITION_WEIGHT - evenPositionSum, CHECK_MODULUS);
        int expectedSecond = (oddPositionSum + evenPositionSum + digits[FIRST_CHECK_DIGIT_INDEX]) % CHECK_MODULUS;
        return digits[FIRST_CHECK_DIGIT_INDEX] == expectedFirst && digits[SECOND_CHECK_DIGIT_INDEX] == expectedSecond;
    }

    private static int sumEveryOtherDigit(int[] digits, int startIndex) {
        int sum = 0;
        for (int i = startIndex; i < FIRST_CHECK_DIGIT_INDEX; i += POSITION_STEP) {
            sum += digits[i];
        }
        return sum;
    }
}
