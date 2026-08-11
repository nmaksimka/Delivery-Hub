package com.deliveryhub.payment.api;

import com.deliveryhub.payment.api.generated.model.PaymentResponse;
import com.deliveryhub.payment.api.generated.model.PaymentState;
import com.deliveryhub.payment.api.generated.model.WalletResponse;
import com.deliveryhub.payment.domain.Payment;
import com.deliveryhub.payment.domain.Wallet;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class ApiModels {

    private ApiModels() {
    }

    static WalletResponse wallet(Wallet wallet) {
        return new WalletResponse(wallet.getUserId(), wallet.getAvailable(), wallet.getHeld(),
                utc(wallet.getUpdatedAt()));
    }

    static PaymentResponse payment(Payment payment) {
        PaymentResponse response = new PaymentResponse(payment.getOrderId(), payment.getUserId(), payment.getAmount(),
                PaymentState.valueOf(payment.getStatus().name()), payment.getProvider(),
                utc(payment.getCreatedAt()), utc(payment.getUpdatedAt()));
        if (payment.getDeclineReason() != null) {
            response.setDeclineReason(payment.getDeclineReason().name());
        }
        return response;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
