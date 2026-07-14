package com.deliveryhub.restaurant.importer;

public enum ImportStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
