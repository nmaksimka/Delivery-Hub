package com.deliveryhub.order.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RestaurantDto {
    private Long id;
    private String name;
    private boolean active;
}
