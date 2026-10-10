package com.apexinsight.model;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public interface Searchable {
    String searchText();

    static <T extends Searchable> List<T> matching(Collection<T> items, String query) {
        if (query == null || query.isBlank()) return List.copyOf(items);
        String term = query.trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> item.searchText().toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }
}
