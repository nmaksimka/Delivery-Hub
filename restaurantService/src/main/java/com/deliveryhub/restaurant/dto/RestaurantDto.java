package com.deliveryhub.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestaurantDto {
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String address;

    @Size(max = 32)
    private String phone;

    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean active;
}
