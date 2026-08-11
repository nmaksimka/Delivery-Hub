package com.deliveryhub.payment.application;

public interface PaymentProvider {

    String name();

    ProviderResult hold(ProviderRequest request);

    ProviderResult capture(ProviderRequest request);

    ProviderResult release(ProviderRequest request);

    ProviderResult refund(ProviderRequest request);
}
