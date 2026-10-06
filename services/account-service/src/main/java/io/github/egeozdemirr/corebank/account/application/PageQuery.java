package io.github.egeozdemirr.corebank.account.application;

/** Zero-based page request. The API layer validates the bounds; this only guards against programming errors. */
public record PageQuery(int page, int size) {

    public PageQuery {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Invalid page request: page=" + page + ", size=" + size);
        }
    }
}
