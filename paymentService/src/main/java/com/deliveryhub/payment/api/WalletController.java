package com.deliveryhub.payment.api;

import com.deliveryhub.payment.api.generated.WalletApi;
import com.deliveryhub.payment.api.generated.model.TopUpRequest;
import com.deliveryhub.payment.api.generated.model.WalletResponse;
import com.deliveryhub.payment.application.OnDemoProvider;
import com.deliveryhub.payment.application.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@OnDemoProvider
public class WalletController implements WalletApi {

    static final String HEADER_REPLAYED = "Idempotent-Replayed";

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public ResponseEntity<WalletResponse> getWallet(Long userId) {
        return ResponseEntity.ok(ApiModels.wallet(wallets.current(userId)));
    }

    @Override
    public ResponseEntity<WalletResponse> topUpWallet(Long userId, String idempotencyKey, TopUpRequest request) {
        WalletService.TopUp topUp = wallets.topUp(userId, idempotencyKey, request.getAmount());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (topUp.replayed()) {
            response.header(HEADER_REPLAYED, "true");
        }
        return response.body(ApiModels.wallet(topUp.wallet()));
    }
}
