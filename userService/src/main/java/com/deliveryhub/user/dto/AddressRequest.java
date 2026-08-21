package com.deliveryhub.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 40) String label,
        @NotBlank @Pattern(regexp = "(?iu)\\s*томск\\s*", message = "Delivery is available only in Томск") String city,
        @NotBlank @Size(max = 160) String street,
        @NotBlank @Size(max = 20) String house,
        @Size(max = 20) String apartment,
        @Size(max = 20) String entrance,
        @Size(max = 20) String floor,
        @Size(max = 300) String courierComment) {
}
