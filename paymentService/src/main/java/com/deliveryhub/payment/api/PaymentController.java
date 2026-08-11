package com.deliveryhub.payment.api;

import com.deliveryhub.payment.api.generated.PaymentsApi;
import com.deliveryhub.payment.api.generated.model.PaymentResponse;
import com.deliveryhub.payment.application.CurrentUser;
import com.deliveryhub.payment.application.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController implements PaymentsApi {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @Override
    public ResponseEntity<PaymentResponse> getOrderPayment(Long orderId, Long userId, String role) {
        return ResponseEntity.ok(ApiModels.payment(payments.findForViewer(orderId, new CurrentUser(userId, role))));
    }
}
