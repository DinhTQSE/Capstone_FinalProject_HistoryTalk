package com.historytalk.service.chat;

import com.historytalk.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatContentPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "địt mẹ", "ĐỊT MẸ", "dit me may", "đ ị t", "đ.m", "d.m.m", "đmm", "vcl", "ông là cái lồn gì",
            "cặc", "lồnnnn", "đ!t mẹ", "chó đẻ", "fuck you", "du ma", "con cac"
    })
    void obsceneLanguageIsBlocked(String content) {
        assertThat(ChatContentPolicy.containsBlockedLanguage(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Buổi học hôm nay", "các tướng lĩnh", "lực lượng lớn", "lon nước ngọt",
            "Ngô Quyền đã giết Kiều Công Tiễn thế nào?", "Vì sao có cuộc thảm sát này?",
            "Dù mẹ ngài có khuyên", "chỗ để thuyền ở đâu", "đeo kiếm bên hông", "đít thuyền bị thủng",
            "Năm 938 quân ta dùng 2 cm cọc", "Sông rộng 5 m", "A. B. C", "Trận Bạch Đằng diễn ra năm nào?"
    })
    void ordinaryAndHistoricalLanguageIsAllowed(String content) {
        assertThat(ChatContentPolicy.containsBlockedLanguage(content)).isFalse();
    }

    @Test
    void decomposedVietnameseTextIsNormalizedFirst() {
        String decomposed = Normalizer.normalize("địt", Normalizer.Form.NFD);

        assertThat(ChatContentPolicy.containsBlockedLanguage(decomposed)).isTrue();
    }

    @Test
    void blockedMessageFailsWithACodeTheFrontendCanRecognize() {
        assertThatThrownBy(() -> ChatContentPolicy.requireAppropriate("địt mẹ"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("INAPPROPRIATE_CONTENT: ");
    }

    @Test
    void emptyContentIsLeftToRequestValidation() {
        assertThatCode(() -> ChatContentPolicy.requireAppropriate(null)).doesNotThrowAnyException();
        assertThatCode(() -> ChatContentPolicy.requireAppropriate("  ")).doesNotThrowAnyException();
    }
}
