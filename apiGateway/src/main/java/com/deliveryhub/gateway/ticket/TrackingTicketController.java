package com.deliveryhub.gateway.ticket;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class TrackingTicketController {

    private final WebSocketTickets tickets;

    public TrackingTicketController(WebSocketTickets tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/api/tracking/ticket")
    public Mono<TicketResponse> issue(@AuthenticationPrincipal Jwt jwt) {
        return tickets.issue(jwt.getTokenValue())
                .map(ticket -> new TicketResponse(ticket, WebSocketTickets.TTL.toSeconds()));
    }

    public record TicketResponse(String ticket, long expiresIn) {
    }
}
