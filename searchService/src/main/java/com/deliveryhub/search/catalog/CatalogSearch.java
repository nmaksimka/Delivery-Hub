package com.deliveryhub.search.catalog;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.deliveryhub.search.api.SearchHitDto;
import com.deliveryhub.search.api.SearchPageDto;
import com.deliveryhub.search.api.SuggestionDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CatalogSearch {

    private final ElasticsearchClient client;
    private final SearchProperties properties;

    public CatalogSearch(ElasticsearchClient client, SearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public SearchPageDto search(String text, SearchKind kind, int page, int size) throws IOException {
        int pageSize = Math.min(size, properties.maxPageSize());
        SearchResponse<CatalogDocument> response = client.search(s -> s
                .index(properties.index())
                .query(CatalogQueries.search(text, kind))
                .from(page * pageSize)
                .size(pageSize)
                .trackTotalHits(t -> t.enabled(true)), CatalogDocument.class);
        List<SearchHitDto> items = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(SearchHitDto::from)
                .toList();
        long total = response.hits().total() == null ? items.size() : response.hits().total().value();
        return new SearchPageDto(items, total, page, pageSize);
    }

    public List<SuggestionDto> suggest(String prefix) throws IOException {
        SearchResponse<CatalogDocument> response = client.search(s -> s
                .index(properties.index())
                .query(CatalogQueries.suggest(prefix))
                .size(properties.suggestions() * 2), CatalogDocument.class);
        Map<String, SuggestionDto> unique = new LinkedHashMap<>();
        response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(SuggestionDto::from)
                .forEach(suggestion -> unique.putIfAbsent(suggestion.text().toLowerCase(), suggestion));
        return unique.values().stream().limit(properties.suggestions()).toList();
    }
}
