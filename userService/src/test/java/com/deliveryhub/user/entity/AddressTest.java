package com.deliveryhub.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressTest {

    private static final Instant NOW = Instant.parse("2026-07-14T09:00:00Z");

    @Test
    void normalizesCityAndDropsBlankOptionalFields() {
        Address address = Address.create(1L, details("  томск ", "  ", "5"), true, NOW);

        assertThat(address.getCity()).isEqualTo("Томск");
        assertThat(address.getApartment()).isNull();
        assertThat(address.getEntrance()).isEqualTo("5");
        assertThat(address.isPrimary()).isTrue();
    }

    @Test
    void rejectsCityOtherThanTomsk() {
        assertThatThrownBy(() -> Address.create(1L, new AddressDetails("Дом", "Москва", "Ленина", "1", null, null, null, null), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeKeepsOwnerAndCreationTime() {
        Address address = Address.create(1L, details("Томск", null, null), false, NOW);
        Instant later = NOW.plusSeconds(60);

        address.change(new AddressDetails("Работа", "Томск", "Красноармейская", "10", "12", null, null, "позвонить"), later);

        assertThat(address.getUserId()).isEqualTo(1L);
        assertThat(address.getLabel()).isEqualTo("Работа");
        assertThat(address.getCreatedAt()).isEqualTo(NOW);
        assertThat(address.getUpdatedAt()).isEqualTo(later);
    }

    private static AddressDetails details(String city, String apartment, String entrance) {
        return new AddressDetails("Дом", city, "Ленина", "1", apartment, entrance, null, null);
    }
}
