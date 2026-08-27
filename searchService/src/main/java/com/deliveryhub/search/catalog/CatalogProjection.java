package com.deliveryhub.search.catalog;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CatalogProjection {

    private static final Logger log = LoggerFactory.getLogger(CatalogProjection.class);

    private final ElasticsearchClient client;
    private final SearchProperties properties;

    public CatalogProjection(ElasticsearchClient client, SearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void apply(RestaurantCatalogChangedEvent event) throws IOException {
        long restaurantId = event.getRestaurantId();
        if (isOutdated(event)) {
            log.debug("Snapshot {} of restaurant {} is older than the indexed one, skipped",
                    event.getEventId(), restaurantId);
            return;
        }
        client.deleteByQuery(d -> d.index(properties.index())
                .query(q -> q.term(t -> t.field("restaurantId").value(restaurantId)))
                .refresh(true));
        BulkResponse response = client.bulk(bulkOf(documentsOf(event)));
        if (response.errors()) {
            throw new IllegalStateException("Elasticsearch rejected part of snapshot " + event.getEventId());
        }
        log.debug("Indexed restaurant {} with {} dishes", restaurantId, event.getDishes().size());
    }

    private boolean isOutdated(RestaurantCatalogChangedEvent event) throws IOException {
        GetResponse<CatalogDocument> indexed = client.get(g -> g.index(properties.index())
                .id(CatalogDocument.restaurantKey(event.getRestaurantId())), CatalogDocument.class);
        return indexed.found() && indexed.source() != null
                && indexed.source().changedAt() > event.getChangedAt().toEpochMilli();
    }

    private static List<CatalogDocument> documentsOf(RestaurantCatalogChangedEvent event) {
        List<CatalogDocument> documents = new ArrayList<>();
        documents.add(CatalogDocument.restaurantOf(event));
        if (!event.getRemoved()) {
            event.getDishes().forEach(dish -> documents.add(CatalogDocument.dishOf(event, dish)));
        }
        return documents;
    }

    private BulkRequest bulkOf(List<CatalogDocument> documents) {
        BulkRequest.Builder bulk = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        documents.forEach(document -> bulk.operations(op -> op.index(i -> i
                .index(properties.index())
                .id(document.key())
                .document(document))));
        return bulk.build();
    }
}
