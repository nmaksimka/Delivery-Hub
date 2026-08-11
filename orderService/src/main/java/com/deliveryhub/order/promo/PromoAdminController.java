package com.deliveryhub.order.promo;

import com.deliveryhub.order.exception.ForbiddenException;
import com.deliveryhub.order.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promo-codes")
public class PromoAdminController {

    private final PromoService promoService;

    public PromoAdminController(PromoService promoService) {
        this.promoService = promoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeDto create(@Valid @RequestBody CreatePromoCodeRequest request,
                               @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return PromoCodeDto.of(promoService.create(request));
    }

    @GetMapping
    public Page<PromoCodeDto> list(@PageableDefault(size = 20) Pageable pageable,
                                   @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return promoService.list(pageable).map(PromoCodeDto::of);
    }

    @PostMapping("/{id}/deactivate")
    public PromoCodeDto deactivate(@PathVariable Long id,
                                   @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return PromoCodeDto.of(promoService.deactivate(id));
    }

    private static void requireAdmin(String role) {
        if (!CurrentUser.isAdmin(role)) {
            throw new ForbiddenException("Only administrators can manage promo codes");
        }
    }
}
