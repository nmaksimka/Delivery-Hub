package com.deliveryhub.restaurant.importer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public record ImportInstance(String id) {

    public static ImportInstance current() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = localHostName();
        }
        return new ImportInstance(hostname == null || hostname.isBlank() ? UUID.randomUUID().toString() : hostname);
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
