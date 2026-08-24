package com.deliveryhub.search.api;

import com.deliveryhub.search.catalog.CatalogDocument;

public record SuggestionDto(String text, String kind, Long restaurantId, Long menuItemId) {

    public static SuggestionDto from(CatalogDocument document) {
        return new SuggestionDto(document.name(), document.kind(), document.restaurantId(), document.menuItemId());
    }
}
