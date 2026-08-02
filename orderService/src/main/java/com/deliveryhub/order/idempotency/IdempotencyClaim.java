package com.deliveryhub.order.idempotency;

public sealed interface IdempotencyClaim {

    record Acquired() implements IdempotencyClaim {
    }

    record Replay(int status, String body) implements IdempotencyClaim {
    }

    record InProgress() implements IdempotencyClaim {
    }

    record Mismatch() implements IdempotencyClaim {
    }
}
