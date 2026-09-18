package com.deliveryhub.restaurant.contract;

import com.deliveryhub.restaurant.controller.CabinetController;
import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.exception.GlobalExceptionHandler;
import com.deliveryhub.restaurant.security.OwnerIdentityResolver;
import com.deliveryhub.restaurant.service.RestaurantCabinet;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public abstract class CabinetContractBase {

    @BeforeEach
    void setUp() {
        RestaurantCabinet cabinet = Mockito.mock(RestaurantCabinet.class);
        when(cabinet.restaurant(eq(42L), eq(7L))).thenReturn(new RestaurantDto(7L, "Томская пекарня",
                "Ленина, 12", "+7 3822 000000", "bakery", "Томск", null, LocalTime.of(8, 0), LocalTime.of(22, 0),
                true, 56.4884, 84.948, 4.5, 12));
        when(cabinet.restaurant(eq(43L), eq(7L))).thenThrow(new EntityNotFoundException("Restaurant 7 not found"));
        RestAssuredMockMvc.standaloneSetup(MockMvcBuilders.standaloneSetup(new CabinetController(cabinet))
                .setCustomArgumentResolvers(new OwnerIdentityResolver())
                .setControllerAdvice(new GlobalExceptionHandler()));
    }
}
