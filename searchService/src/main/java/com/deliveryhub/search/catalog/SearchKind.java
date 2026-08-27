package com.deliveryhub.search.catalog;

import java.util.List;

public enum SearchKind {
    ALL(List.of(CatalogDocument.RESTAURANT, CatalogDocument.DISH)),
    RESTAURANT(List.of(CatalogDocument.RESTAURANT)),
    DISH(List.of(CatalogDocument.DISH));

    private final List<String> documentKinds;

    SearchKind(List<String> documentKinds) {
        this.documentKinds = documentKinds;
    }

    public List<String> documentKinds() {
        return documentKinds;
    }
}
