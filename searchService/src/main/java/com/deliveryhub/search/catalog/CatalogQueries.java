package com.deliveryhub.search.catalog;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import java.util.List;

public final class CatalogQueries {

    private static final List<String> SEARCH_FIELDS =
            List.of("name^3", "category^2", "restaurantName^2", "cuisine", "description");

    private CatalogQueries() {
    }

    public static Query search(String text, SearchKind kind) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.multiMatch(mm -> mm
                        .query(text)
                        .fields(SEARCH_FIELDS)
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                        .prefixLength(1)))
                .should(s -> s.match(mt -> mt.field("name.autocomplete").query(text).operator(Operator.And)))
                .minimumShouldMatch("1")
                .filter(visible(kind))));
    }

    public static Query suggest(String prefix) {
        return Query.of(q -> q.bool(b -> b
                .must(m -> m.match(mt -> mt.field("name.autocomplete").query(prefix).operator(Operator.And)))
                .filter(visible(SearchKind.ALL))));
    }

    private static List<Query> visible(SearchKind kind) {
        return List.of(
                Query.of(q -> q.terms(t -> t.field("kind").terms(v -> v.value(kind.documentKinds().stream()
                        .map(FieldValue::of)
                        .toList())))),
                Query.of(q -> q.term(t -> t.field("active").value(true))),
                Query.of(q -> q.term(t -> t.field("available").value(true))));
    }
}
