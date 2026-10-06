package io.github.egeozdemirr.corebank.account.support;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.datafaker.Faker;

/**
 * Synthetic test data only: names from Faker's Turkish locale and TCKNs that satisfy the check digit algorithm
 * but are generated at random, so they do not identify real people.
 */
public final class SyntheticData {

    private static final Faker FAKER = new Faker(Locale.forLanguageTag("tr"));

    private SyntheticData() {
    }

    public static String fullName() {
        return FAKER.name().fullName();
    }

    /** Computes the check digits independently of the production code, so tests can catch an algorithm bug. */
    public static String tckn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] digits = new int[11];
        digits[0] = random.nextInt(1, 10);
        for (int i = 1; i < 9; i++) {
            digits[i] = random.nextInt(10);
        }
        int odd = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int even = digits[1] + digits[3] + digits[5] + digits[7];
        digits[9] = Math.floorMod(odd * 7 - even, 10);
        digits[10] = (odd + even + digits[9]) % 10;
        StringBuilder value = new StringBuilder();
        for (int digit : digits) {
            value.append(digit);
        }
        return value.toString();
    }
}
