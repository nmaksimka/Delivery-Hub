package com.deliveryhub.order.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void rejectsNonPositiveRestaurantId() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = VALIDATOR.validate(
                new CreateOrderRequest(-1L, List.of(new OrderItemRequest(10L, 1))));

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("restaurantId");
    }

    @Test
    void rejectsNonPositiveMenuItemId() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = VALIDATOR.validate(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(0L, 1))));

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("orderItemsRequest[0].menuItemId");
    }

    @Test
    void acceptsValidRequest() {
        assertThat(VALIDATOR.validate(new CreateOrderRequest(1L, List.of(new OrderItemRequest(10L, 2))))).isEmpty();
    }
}
