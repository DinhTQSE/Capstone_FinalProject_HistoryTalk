package com.historytalk.service.speech;

import com.historytalk.exception.InvalidRequestException;

import java.util.List;

/**
 * Preset voices of the self-hosted VieNeu-TTS v3 Turbo server (Apache-2.0).
 * The TTS server identifies a voice by its display name.
 */
public final class CharacterVoices {

    public record VoiceOption(String id, String gender, String region, String style, boolean recommended) {
    }

    /** Male, northern, storytelling: used until a character is given its own voice. */
    public static final String DEFAULT_VOICE = "Thiện Minh";

    public static final List<VoiceOption> OPTIONS = List.of(
            new VoiceOption("Thiện Minh", "MALE", "NORTH", "Kể chuyện", true),
            new VoiceOption("Thiền Tâm Đức", "MALE", "NORTH", "Kể chuyện", true),
            new VoiceOption("Hải Đăng", "MALE", "NORTH", "Tự nhiên", true),
            new VoiceOption("Adam bựa", "MALE", "NORTH", "Tự nhiên", true),
            new VoiceOption("Quang Sơn", "MALE", "CENTRAL", "Tự nhiên", true),
            new VoiceOption("Minh Đức", "MALE", "NORTH", "Tin tức", false),
            new VoiceOption("Phạm Tuyên", "MALE", "NORTH", "Tự nhiên", false),
            new VoiceOption("Xuân Vĩnh", "MALE", "NORTH", "Tự nhiên", false),
            new VoiceOption("Quốc Tuấn", "MALE", "NORTH", "Tự nhiên", false),
            new VoiceOption("Thanh Bình", "MALE", "NORTH", "Kể chuyện", false),
            new VoiceOption("Thái Sơn", "MALE", "SOUTH", "Kể chuyện", false),
            new VoiceOption("Minh Triết", "MALE", "SOUTH", "Tin tức", false),
            new VoiceOption("Đức Trí", "MALE", "SOUTH", "Đọc truyện", false),
            new VoiceOption("Adam", "MALE", "SOUTH", "Tự nhiên", false),
            new VoiceOption("Trúc Ly", "FEMALE", "NORTH", "Tự nhiên", true),
            new VoiceOption("Mai Anh", "FEMALE", "NORTH", "Tin tức", true),
            new VoiceOption("Ngọc Huyền", "FEMALE", "NORTH", "Tự nhiên", true),
            new VoiceOption("Thùy Dung", "FEMALE", "SOUTH", "Tin tức", true),
            new VoiceOption("Ngọc Trân", "FEMALE", "CENTRAL", "Tự nhiên", true),
            new VoiceOption("Ngọc Linh", "FEMALE", "NORTH", "Kể chuyện", false),
            new VoiceOption("Đoan Trang", "FEMALE", "NORTH", "Tự nhiên", false),
            new VoiceOption("Quỳnh Anh", "FEMALE", "NORTH", "Đọc truyện", false),
            new VoiceOption("Thục Đoan", "FEMALE", "SOUTH", "Kể chuyện", false),
            new VoiceOption("Mỹ Duyên", "FEMALE", "SOUTH", "Đọc truyện", false),
            new VoiceOption("Kim Thanh", "FEMALE", "SOUTH", "Đọc truyện", false));

    public static final List<String> SUPPORTED = OPTIONS.stream().map(VoiceOption::id).toList();

    private CharacterVoices() {
    }

    public static String resolve(String voiceId) {
        return voiceId != null && SUPPORTED.contains(voiceId) ? voiceId : DEFAULT_VOICE;
    }

    /** Returns the voice to store, or null to use the default voice. */
    public static String requireSupported(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return null;
        }
        if (!SUPPORTED.contains(voiceId.trim())) {
            throw new InvalidRequestException("Giọng đọc không hợp lệ. Các giọng hỗ trợ: " + String.join(", ", SUPPORTED));
        }
        return voiceId.trim();
    }
}
