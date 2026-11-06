package com.historytalk.service.speech;

import com.historytalk.exception.InvalidRequestException;

import java.util.List;

/**
 * Preset voices of the self-hosted VieNeu-TTS v3 Turbo server (Apache-2.0).
 * The TTS server identifies a voice by its display name.
 */
public final class CharacterVoices {

    /** Male, northern, storytelling: used until a character is given its own voice. */
    public static final String DEFAULT_VOICE = "Thiện Minh";

    public static final List<String> SUPPORTED = List.of(
            "Thiện Minh", "Thiền Tâm Đức", "Hải Đăng", "Adam bựa", "Quang Sơn", "Minh Đức", "Phạm Tuyên",
            "Xuân Vĩnh", "Quốc Tuấn", "Thanh Bình", "Thái Sơn", "Minh Triết", "Đức Trí", "Adam",
            "Trúc Ly", "Mai Anh", "Ngọc Huyền", "Thùy Dung", "Ngọc Trân", "Ngọc Linh", "Đoan Trang",
            "Quỳnh Anh", "Thục Đoan", "Mỹ Duyên", "Kim Thanh");

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
