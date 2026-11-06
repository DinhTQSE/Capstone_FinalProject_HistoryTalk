package com.historytalk.service.character;

import com.historytalk.entity.character.Character;
import com.historytalk.exception.InvalidRequestException;

import java.util.regex.Pattern;

/**
 * How a character refers to itself and to the learner, e.g. "ta" and "ngươi" for an ancient king.
 * Characters without a preference speak with "tôi" and "bạn".
 */
public final class CharacterPronouns {

    public static final String DEFAULT_SELF = "tôi";
    public static final String DEFAULT_ADDRESS = "bạn";
    public static final int MAX_LENGTH = 30;

    private static final Pattern ALLOWED = Pattern.compile("[\\p{L} ]+");

    private CharacterPronouns() {
    }

    /** Returns the pronoun to store, or null to use the default. */
    public static String normalize(String pronoun) {
        if (pronoun == null || pronoun.isBlank()) {
            return null;
        }
        String trimmed = pronoun.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > MAX_LENGTH || !ALLOWED.matcher(trimmed).matches()) {
            throw new InvalidRequestException("Cách xưng hô chỉ gồm chữ cái, tối đa " + MAX_LENGTH + " ký tự");
        }
        return trimmed;
    }

    public static String selfOf(Character character) {
        return orDefault(character.getSelfPronoun(), DEFAULT_SELF);
    }

    public static String addressOf(Character character) {
        return orDefault(character.getAddressPronoun(), DEFAULT_ADDRESS);
    }

    public static String capitalize(String pronoun) {
        return pronoun.isEmpty() ? pronoun : pronoun.substring(0, 1).toUpperCase() + pronoun.substring(1);
    }

    private static String orDefault(String pronoun, String fallback) {
        return pronoun == null || pronoun.isBlank() ? fallback : pronoun;
    }
}
