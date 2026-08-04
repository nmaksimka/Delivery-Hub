package com.deliveryhub.payment.application;

import com.deliveryhub.payment.domain.ErrorCode;

public sealed interface ProviderResult {

    record Approved(String reference) implements ProviderResult {
    }

    record Declined(ErrorCode reason) implements ProviderResult {
    }
}
