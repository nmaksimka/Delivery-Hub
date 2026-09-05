package com.deliveryhub.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must contain 10-15 digits")
    private String phone;
}
