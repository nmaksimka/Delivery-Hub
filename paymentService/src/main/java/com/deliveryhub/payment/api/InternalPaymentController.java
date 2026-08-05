package com.deliveryhub.payment.api;

import com.deliveryhub.payment.api.generated.InternalPaymentsApi;
import com.deliveryhub.payment.api.generated.model.HoldRequest;
import com.deliveryhub.payment.api.generated.model.PaymentResponse;
import com.deliveryhub.payment.application.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalPaymentController implements InternalPaymentsApi {

    private final PaymentService payments;

    public InternalPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @Override
    public ResponseEntity<PaymentResponse> holdPayment(Long orderId, HoldRequest request) {
        return ResponseEntity.ok(ApiModels.payment(payments.hold(orderId, request.getUserId(), request.getAmount())));
    }

    @Override
    public ResponseEntity<PaymentResponse> capturePayment(Long orderId) {
        return ResponseEntity.ok(ApiModels.payment(payments.capture(orderId)));
    }

    @Override
    public ResponseEntity<PaymentResponse> releasePayment(Long orderId) {
        return ResponseEntity.ok(ApiModels.payment(payments.release(orderId)));
    }

    @Override
    public ResponseEntity<PaymentResponse> refundPayment(Long orderId) {
        return ResponseEntity.ok(ApiModels.payment(payments.refund(orderId)));
    }
}
