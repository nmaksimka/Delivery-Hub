package com.deliveryhub.delivery.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void concurrentUpdateIsConflictWithoutEntityDetails() {
        ProblemDetail problem = handler.handleConcurrentUpdate(
                new ObjectOptimisticLockingFailureException("com.deliveryhub.delivery.entity.Delivery", 15L));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Concurrent update");
        assertThat(problem.getDetail()).doesNotContain("entity").doesNotContain("15");
    }
}
