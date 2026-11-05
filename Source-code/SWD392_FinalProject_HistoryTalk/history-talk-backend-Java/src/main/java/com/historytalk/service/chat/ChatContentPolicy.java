package com.historytalk.service.chat;

import com.historytalk.exception.InvalidRequestException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects obscene chat messages before they are saved or reach the LLM.
 * Words keep their Vietnamese diacritics on purpose: without them, ordinary words such as
 * "buổi", "các" and "lớn" would look identical to obscene ones. History terms such as
 * "giết" or "thảm sát" are never listed, because learners ask about them legitimately.
 */
public final class ChatContentPolicy {

    private static final String INAPPROPRIATE_CONTENT_MESSAGE =
            "INAPPROPRIATE_CONTENT: Phát hiện ngôn từ không phù hợp. Vui lòng điều chỉnh nội dung rồi gửi lại.";

    private static final Set<String> BLOCKED_WORDS = Set.of(
            "lồn", "loz", "cặc", "cặk", "kặc", "buồi", "địt", "đit", "đjt", "djt", "đụ", "đéo", "đĩ",
            "đm", "đmm", "đcm", "dm", "dmm", "dcm", "clm", "cmm", "vcl", "vkl",
            "ditme", "dume", "duma",
            "fuck", "fck", "fucking", "motherfucker", "shit", "bitch", "asshole");

    // Unaccented on purpose: "dù mẹ" and "chỗ để" keep their diacritics, so they do not match.
    private static final List<String> BLOCKED_PHRASES = List.of(
            "dit me", "dit cu", "dit con", "du ma", "du me", "con cac", "cai lon", "chó đẻ");

    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Pattern BANG_INSIDE_WORD = Pattern.compile("(?<=\\p{L})!(?=\\p{L})");
    private static final Pattern REPEATED_LETTER = Pattern.compile("(\\p{L})\\1+");

    private ChatContentPolicy() {
    }

    public static void requireAppropriate(String content) {
        if (containsBlockedLanguage(content)) {
            throw new InvalidRequestException(INAPPROPRIATE_CONTENT_MESSAGE);
        }
    }

    static boolean containsBlockedLanguage(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String normalized = Normalizer.normalize(content, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        normalized = BANG_INSIDE_WORD.matcher(normalized).replaceAll("i");

        List<String> words = joinSpacedLetters(normalized);
        for (String word : words) {
            if (BLOCKED_WORDS.contains(word)
                    || BLOCKED_WORDS.contains(REPEATED_LETTER.matcher(word).replaceAll("$1"))) {
                return true;
            }
        }

        String joined = " " + String.join(" ", words) + " ";
        return BLOCKED_PHRASES.stream().anyMatch(phrase -> joined.contains(" " + phrase + " "));
    }

    /** Turns "đ ị t" and "d.m" into one word, so spacing letters out does not bypass the check. */
    private static List<String> joinSpacedLetters(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder spaced = new StringBuilder();

        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.codePointCount(0, word.length()) == 1) {
                spaced.append(word);
                continue;
            }
            if (!spaced.isEmpty()) {
                words.add(spaced.toString());
                spaced.setLength(0);
            }
            words.add(word);
        }
        if (!spaced.isEmpty()) {
            words.add(spaced.toString());
        }
        return words;
    }
}
