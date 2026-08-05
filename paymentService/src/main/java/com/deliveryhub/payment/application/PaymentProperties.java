package com.deliveryhub.payment.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("app.payment")
public record PaymentProperties(
        @NotBlank @DefaultValue("demo") String provider,
        @Valid @NotNull @DefaultValue Demo demo) {

    public record Demo(@NotNull @Positive @DefaultValue("100000.00") BigDecimal maxBalance) {
    }
}
