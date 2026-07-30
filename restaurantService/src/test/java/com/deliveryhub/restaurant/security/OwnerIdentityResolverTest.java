package com.deliveryhub.restaurant.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerIdentityResolverTest {

    private final OwnerIdentityResolver resolver = new OwnerIdentityResolver();

    @Test
    void takesOwnerIdFromGatewayHeaders() {
        assertThat(resolve("42", "RESTAURANT_OWNER")).isEqualTo(new OwnerIdentity(42L));
    }

    @Test
    void rejectsOtherRoles() {
        assertThatThrownBy(() -> resolve("42", "USER")).isInstanceOf(OwnerOnlyException.class);
        assertThatThrownBy(() -> resolve("42", "ADMIN")).isInstanceOf(OwnerOnlyException.class);
        assertThatThrownBy(() -> resolve("42", null)).isInstanceOf(OwnerOnlyException.class);
    }

    @Test
    void rejectsMissingOrBrokenUserId() {
        assertThatThrownBy(() -> resolve(null, "RESTAURANT_OWNER")).isInstanceOf(OwnerOnlyException.class);
        assertThatThrownBy(() -> resolve("abc", "RESTAURANT_OWNER")).isInstanceOf(OwnerOnlyException.class);
    }

    private OwnerIdentity resolve(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(OwnerIdentityResolver.HEADER_USER_ID, userId);
        }
        if (role != null) {
            request.addHeader(OwnerIdentityResolver.HEADER_USER_ROLE, role);
        }
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }
}
