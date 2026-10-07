package io.github.egeozdemirr.corebank.transfer.domain.user;

import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidUserIdException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A user acting on a transfer (maker or checker). Until authentication arrives (roadmap week 4) it comes from the
 * {@code X-Actor-User-Id} header; the same format is accepted then for the subject of the token.
 */
public record UserId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    public UserId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidUserIdException();
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
