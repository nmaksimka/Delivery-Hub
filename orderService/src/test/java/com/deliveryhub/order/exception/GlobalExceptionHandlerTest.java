package com.deliveryhub.order.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void concurrentUpdateIsConflictWithoutEntityDetails() {
        ProblemDetail problem = handler.handleConcurrentUpdate(
                new ObjectOptimisticLockingFailureException("com.deliveryhub.order.entity.Order", 15L));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Concurrent update");
        assertThat(problem.getDetail()).doesNotContain("entity").doesNotContain("15");
    }

    @Test
    void rejectionCarriesMachineReadableCode() {
        ProblemDetail problem = handler.handleConflict(
                new OrderRejectedException(ErrorCode.RESTAURANT_CLOSED, "Restaurant 1 is closed now"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", ErrorCode.RESTAURANT_CLOSED);
    }

    @Test
    void notFoundUsesSpecificCodeWhenKnownAndGenericOtherwise() {
        ProblemDetail menuItem = handler.handleNotFound(
                new ItemNotFoundException(ErrorCode.ITEM_NOT_FOUND, "Menu item with ID: 5 was not found"));
        ProblemDetail order = handler.handleNotFound(new EntityNotFoundException("Order with ID: 1 was not found"));

        assertThat(menuItem.getProperties()).containsEntry("code", ErrorCode.ITEM_NOT_FOUND);
        assertThat(order.getProperties()).containsEntry("code", ErrorCode.NOT_FOUND);
    }

    @Test
    void concurrentUpdateAndUnexpectedErrorsHaveCodes() {
        ProblemDetail concurrent = handler.handleConcurrentUpdate(
                new ObjectOptimisticLockingFailureException("Order", 1L));
        ProblemDetail unexpected = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(concurrent.getProperties()).containsEntry("code", ErrorCode.CONCURRENT_UPDATE);
        assertThat(unexpected.getProperties()).containsEntry("code", ErrorCode.INTERNAL_ERROR);
    }
}
