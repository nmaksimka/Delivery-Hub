package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSourcesTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.5", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "::1", "fd00::1", "fe80::1"})
    void treatsPrivateAndLoopbackAddressesAsInternal(String address) throws UnknownHostException {
        assertThat(ImageSources.isInternal(InetAddress.getByName(address))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "2606:4700::1111"})
    void treatsPublicAddressesAsExternal(String address) throws UnknownHostException {
        assertThat(ImageSources.isInternal(InetAddress.getByName(address))).isFalse();
    }
}
