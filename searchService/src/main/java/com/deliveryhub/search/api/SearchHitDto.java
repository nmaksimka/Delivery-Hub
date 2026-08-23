package com.deliveryhub.search.api;

import com.deliveryhub.search.catalog.CatalogDocument;

import java.math.BigDecimal;

public record SearchHitDto(
        String kind,
        Long restaurantId,
        Long menuItemId,
        String name,
        String description,
        String category,
        String restaurantName,
        BigDecimal price) {

    public static SearchHitDto from(CatalogDocument document) {
        return new SearchHitDto(document.kind(), document.restaurantId(), document.menuItemId(), document.name(),
                document.description(), document.category(), document.restaurantName(), document.price());
    }
}
