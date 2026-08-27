package com.deliveryhub.order.analytics;

import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/admin/analytics")
    public AnalyticsReport forAdmin(
            @RequestHeader(name = "X-User-Role", required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long restaurantId) {
        if (!CurrentUser.isAdmin(role)) {
            throw new ForbiddenException("Only administrators can view platform analytics");
        }
        return analyticsService.forAdmin(from, to, restaurantId);
    }

    @GetMapping("/api/cabinet/analytics")
    public AnalyticsReport forOwner(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.forOwner(userId, restaurantId, from, to);
    }
}
