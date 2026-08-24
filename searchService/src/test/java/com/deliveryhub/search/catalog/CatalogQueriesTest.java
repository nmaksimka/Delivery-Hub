package com.deliveryhub.search.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogQueriesTest {

    @Test
    void searchIsFuzzyAndHidesInactiveOrUnavailableEntries() {
        String query = CatalogQueries.search("пельмени", SearchKind.ALL).toString();

        assertThat(query)
                .contains("\"fuzziness\":\"AUTO\"")
                .contains("\"name^3\"")
                .contains("\"RESTAURANT\"", "\"DISH\"")
                .contains("\"active\":{\"value\":true}")
                .contains("\"available\":{\"value\":true}");
    }

    @Test
    void searchByKindKeepsOnlyThatKind() {
        String query = CatalogQueries.search("щи", SearchKind.DISH).toString();

        assertThat(query).contains("\"DISH\"").doesNotContain("\"RESTAURANT\"");
    }

    @Test
    void suggestMatchesEveryWordAsPrefix() {
        String query = CatalogQueries.suggest("пел бул").toString();

        assertThat(query).contains("name.autocomplete").contains("\"operator\":\"and\"");
    }
}
