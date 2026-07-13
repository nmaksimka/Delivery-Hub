package com.deliveryhub.restaurant.importer;

public final class MealDescription {

    private static final int MAX_LENGTH = 240;
    private static final int MIN_SENTENCE_CUT = 80;

    private MealDescription() {
    }

    public static String fromInstructions(String instructions) {
        if (instructions == null || instructions.isBlank()) {
            return null;
        }
        String text = instructions.replaceAll("\\s+", " ").strip();
        if (text.length() <= MAX_LENGTH) {
            return text;
        }
        String head = text.substring(0, MAX_LENGTH);
        int sentenceEnd = Math.max(head.lastIndexOf(". "), head.lastIndexOf("! "));
        if (sentenceEnd >= MIN_SENTENCE_CUT) {
            return head.substring(0, sentenceEnd + 1);
        }
        int wordEnd = head.lastIndexOf(' ');
        return (wordEnd > 0 ? head.substring(0, wordEnd) : head) + "…";
    }
}
