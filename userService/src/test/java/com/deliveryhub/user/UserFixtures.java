package com.deliveryhub.user;

import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixtures {

    private UserFixtures() {
    }

    public static User persistedUser(long id, String email, Role role) {
        User user = User.register(email, "$2a$hashed", "Test", null);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }
}
