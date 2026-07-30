package com.deliveryhub.user.dto;

import com.deliveryhub.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
}
