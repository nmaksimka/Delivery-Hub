package com.deliveryhub.restaurant.storage;

import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ImageSources {

    private final Set<String> schemes;
    private final Set<String> hosts;
    private final HostResolver resolver;

    public ImageSources(StorageProperties properties, HostResolver resolver) {
        this.schemes = lowerCase(properties.mirror().allowedSchemes());
        this.hosts = lowerCase(properties.mirror().allowedHosts());
        this.resolver = resolver;
    }

    public boolean permits(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!schemes.contains(uri.getScheme().toLowerCase(Locale.ROOT)) || !hosts.contains(host)) {
            return false;
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            return addresses.length > 0 && Arrays.stream(addresses).noneMatch(ImageSources::isInternal);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xFE) == 0xFC;
        }
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 0 || (first == 100 && second >= 64 && second <= 127);
    }

    private static Set<String> lowerCase(Set<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    @FunctionalInterface
    public interface HostResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
