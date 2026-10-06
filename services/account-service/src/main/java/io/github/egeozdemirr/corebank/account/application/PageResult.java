package io.github.egeozdemirr.corebank.account.application;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public PageResult {
        items = List.copyOf(items);
    }
}
