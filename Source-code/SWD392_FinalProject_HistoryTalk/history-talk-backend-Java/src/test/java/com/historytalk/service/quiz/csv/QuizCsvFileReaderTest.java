package com.historytalk.service.quiz.csv;

import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizCsvFileReaderTest {

    private static final String HEADER = "title,contextId,level,questionContent\n";

    private final QuizCsvFileReader reader = new QuizCsvFileReader();

    @Test
    void readsPlainUtf8() {
        List<CSVRecord> records = reader.read(file(utf8(HEADER + "Trận Bạch Đằng,ctx,EASY,Ai chỉ huy?\n")));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("title")).isEqualTo("Trận Bạch Đằng");
        assertThat(QuizCsvFileReader.lineLabel(records.get(0))).isEqualTo("Dòng 2");
    }

    @Test
    void lineLabelCountsFromHeader() {
        List<CSVRecord> records = reader.read(file(utf8(HEADER + "A,ctx,EASY,Q1\nA,ctx,EASY,Q2\n")));

        assertThat(QuizCsvFileReader.lineLabel(records.get(1))).isEqualTo("Dòng 3");
    }

    @Test
    void stripsUtf8Bom() {
        byte[] body = utf8(HEADER + "Trận Bạch Đằng,ctx,EASY,Q\n");
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        List<CSVRecord> records = reader.read(file(withBom));

        assertThat(records.get(0).get("title")).isEqualTo("Trận Bạch Đằng");
    }

    @Test
    void rejectsWindows1252Bytes() {
        byte[] ansi = (HEADER + "Lê Lợi,ctx,EASY,Q\n").getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> reader.read(file(ansi)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("CSV UTF-8");
    }

    @Test
    void rejectsMojibakeText() {
        assertThatThrownBy(() -> reader.read(file(utf8(HEADER + "Tráº­n Báº¡ch Äáº±ng,ctx,EASY,Q\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Dòng 2")
                .hasMessageContaining("lỗi font");
    }

    @Test
    void acceptsLegitVietnamese() {
        String rows = "NGÃ BA ĐỒNG LỘC,ctx,EASY,Ngô Quyền\n"
                + "Điện Biên Phủ,ctx,EASY,Đ\n"
                + "Ngã ba,ctx,EASY,Ã\n";

        assertThatCode(() -> reader.read(file(utf8(HEADER + rows)))).doesNotThrowAnyException();
    }

    @Test
    void semicolonDelimitedFileGivesHint() {
        assertThatThrownBy(() -> reader.read(file(utf8("title;contextId;level;questionContent\nA;ctx;EASY;Q\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("dấu phẩy");
    }

    @Test
    void missingQuestionContentHeaderFails() {
        assertThatThrownBy(() -> reader.read(file(utf8("title,contextId,level\nA,ctx,EASY\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("File CSV thiếu cột tiêu đề bắt buộc: 'questionContent'");
    }

    @Test
    void missingExplanationHeaderIsAllowed() {
        assertThatCode(() -> reader.read(file(utf8(HEADER + "A,ctx,EASY,Q\n")))).doesNotThrowAnyException();
    }

    @Test
    void headerOnlyFileFails() {
        assertThatThrownBy(() -> reader.read(file(utf8(HEADER))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("File CSV không chứa hàng dữ liệu hợp lệ nào");
    }

    @Test
    void nonCsvExtensionFails() {
        MockMultipartFile xlsx = new MockMultipartFile("file", "q.xlsx", "application/octet-stream", utf8(HEADER));

        assertThatThrownBy(() -> reader.read(xlsx))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Chỉ chấp nhận file .csv");
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "q.csv", "text/csv", bytes);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
