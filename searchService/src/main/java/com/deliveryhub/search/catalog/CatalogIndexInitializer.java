package com.deliveryhub.search.catalog;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class CatalogIndexInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(CatalogIndexInitializer.class);
    private static final String DEFINITION = "elasticsearch/catalog-index.json";

    private final ElasticsearchClient client;
    private final SearchProperties properties;

    public CatalogIndexInitializer(ElasticsearchClient client, SearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (client.indices().exists(e -> e.index(properties.index())).value()) {
            return;
        }
        try (InputStream definition = new ClassPathResource(DEFINITION).getInputStream()) {
            client.indices().create(c -> c.index(properties.index()).withJson(definition));
        }
        log.info("Created search index {}", properties.index());
    }
}
