package com.deliveryhub.restaurant.importer;

public class ImportAlreadyRunningException extends RuntimeException {

    public ImportAlreadyRunningException(ImportSource source) {
        super("Import from " + source + " is already running");
    }
}
