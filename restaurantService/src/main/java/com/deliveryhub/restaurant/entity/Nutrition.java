package com.deliveryhub.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Embeddable
public record Nutrition(
        @Column(name = "energy_kcal", precision = 7, scale = 1) BigDecimal energyKcal,
        @Column(name = "protein_g", precision = 7, scale = 1) BigDecimal proteinGrams,
        @Column(name = "fat_g", precision = 7, scale = 1) BigDecimal fatGrams,
        @Column(name = "carbs_g", precision = 7, scale = 1) BigDecimal carbsGrams,
        @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "nutri_score", length = 1) NutriScore nutriScore) {

    public boolean isEmpty() {
        return energyKcal == null && proteinGrams == null && fatGrams == null && carbsGrams == null
                && nutriScore == null;
    }
}
