package com.deliveryhub.restaurant.importer;

import java.util.UUID;

public interface CatalogImport {

    ImportSource source();

    void run(UUID jobId);
}
