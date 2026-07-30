package com.deliveryhub.restaurant.importer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MealDescriptionTest {

    @Test
    void shortInstructionsAreKeptWithCollapsedWhitespace() {
        assertThat(MealDescription.fromInstructions("Preheat oven.\r\n\r\n  Bake 20 minutes. "))
                .isEqualTo("Preheat oven. Bake 20 minutes.");
    }

    @Test
    void longInstructionsAreCutAtSentenceEnd() {
        String sentence = "Mix flour with water and knead the dough until it is smooth and elastic. ";

        String description = MealDescription.fromInstructions(sentence.repeat(10));

        assertThat(description).hasSizeLessThanOrEqualTo(240).endsWith("elastic.");
    }

    @Test
    void textWithoutSentencesIsCutAtWordWithEllipsis() {
        String description = MealDescription.fromInstructions("word ".repeat(100));

        assertThat(description).hasSizeLessThanOrEqualTo(241).endsWith("word…");
    }

    @Test
    void blankInstructionsGiveNoDescription() {
        assertThat(MealDescription.fromInstructions("  ")).isNull();
        assertThat(MealDescription.fromInstructions(null)).isNull();
    }
}
