package com.deliveryhub.search.catalog;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

@Component
public class CatalogIndexInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(CatalogIndexInitializer.class);
    private static final String DEFINITION = "elasticsearch/catalog-index.json";
    private static final String SYNONYMS = "elasticsearch/synonyms-ru-en.txt";

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
        String definition = definitionWithSynonyms();
        client.indices().create(c -> c.index(properties.index()).withJson(new StringReader(definition)));
        log.info("Created search index {}", properties.index());
    }

    static String definitionWithSynonyms() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        ObjectNode definition;
        try (InputStream json = new ClassPathResource(DEFINITION).getInputStream()) {
            definition = (ObjectNode) mapper.readTree(json);
        }
        ArrayNode synonyms = (ArrayNode) definition.at("/settings/analysis/filter/ru_en_synonyms/synonyms");
        new ClassPathResource(SYNONYMS).getContentAsString(StandardCharsets.UTF_8).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .forEach(synonyms::add);
        return mapper.writeValueAsString(definition);
    }
}
