package com.deliveryhub.delivery.controller;

import com.deliveryhub.delivery.quote.DeliveryQuotes;
import com.deliveryhub.delivery.quote.QuoteDto;
import com.deliveryhub.delivery.quote.QuoteRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryQuoteController {

    private final DeliveryQuotes quotes;

    public DeliveryQuoteController(DeliveryQuotes quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/internal/delivery/quote")
    public QuoteDto quote(@Valid @RequestBody QuoteRequest request) {
        return quotes.quote(request);
    }
}
