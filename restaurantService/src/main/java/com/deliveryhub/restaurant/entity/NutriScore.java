package com.deliveryhub.restaurant.entity;

import java.util.Optional;

public enum NutriScore {
    A, B, C, D, E;

    public static Optional<NutriScore> parse(String grade) {
        if (grade == null || grade.length() != 1) {
            return Optional.empty();
        }
        char letter = Character.toUpperCase(grade.charAt(0));
        return letter >= 'A' && letter <= 'E' ? Optional.of(values()[letter - 'A']) : Optional.empty();
    }
}
