package com.deliveryhub.delivery.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("app.dispatch")
public record DispatchProperties(@DefaultValue List<Long> courierIds) {

    public DispatchProperties {
        courierIds = List.copyOf(courierIds);
    }
}
