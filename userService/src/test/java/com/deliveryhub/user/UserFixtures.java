package com.deliveryhub.user;

import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

public final class UserFixtures {

    public static final Instant REGISTERED_AT = Instant.parse("2026-07-14T09:00:00Z");

    private UserFixtures() {
    }

    public static User persistedUser(long id, String email, Role role) {
        User user = User.register(email, "$2a$hashed", "Test", null, REGISTERED_AT);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }
}
