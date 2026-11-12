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

    private static final String HEADER = "level,questionContent\n";

    private final QuizCsvFileReader reader = new QuizCsvFileReader();

    @Test
    void readsPlainUtf8() {
        List<CSVRecord> records = reader.read(file(utf8(HEADER + "EASY,Ai chỉ huy trận Bạch Đằng?\n")));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("questionContent")).isEqualTo("Ai chỉ huy trận Bạch Đằng?");
        assertThat(QuizCsvFileReader.lineLabel(records.get(0))).isEqualTo("Câu hỏi 1");
    }

    @Test
    void lineLabelIsQuestionNumber() {
        List<CSVRecord> records = reader.read(file(utf8("# guide\n" + HEADER + "EASY,Q1\n# note\nEASY,Q2\n")));

        assertThat(QuizCsvFileReader.lineLabel(records.get(0))).isEqualTo("Câu hỏi 1");
        assertThat(QuizCsvFileReader.lineLabel(records.get(1))).isEqualTo("Câu hỏi 2");
    }

    @Test
    void commentLinesAreSkippedBeforeHeaderAndBetweenRows() {
        List<CSVRecord> records = reader.read(file(utf8(
                "# guide\n#\nlevel,questionContent\nEASY,Câu 1\n# note\nMEDIUM,Câu 2\n")));

        assertThat(records).hasSize(2);
        assertThat(records.get(1).get("questionContent")).isEqualTo("Câu 2");
    }

    @Test
    void excelResavedCommentWithCommasIsSkipped() {
        List<CSVRecord> records = reader.read(file(utf8(
                "# Lưu ý, đọc kỹ,,,,\nlevel,questionContent\nEASY,Câu 1\n")));

        assertThat(records).hasSize(1);
    }

    @Test
    void commentsAndHeaderOnlyFileFails() {
        assertThatThrownBy(() -> reader.read(file(utf8("# guide\n" + HEADER))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("không chứa hàng dữ liệu hợp lệ nào");
    }

    @Test
    void fileWithoutTitleAndContextIdColumnsIsAccepted() {
        assertThat(reader.read(file(utf8(HEADER + "EASY,Câu 1\n")))).hasSize(1);
    }

    @Test
    void missingLevelHeaderFails() {
        assertThatThrownBy(() -> reader.read(file(utf8("questionContent\nCâu 1\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("'level'");
    }

    @Test
    void stripsUtf8Bom() {
        byte[] body = utf8(HEADER + "EASY,Trận Bạch Đằng\n");
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        List<CSVRecord> records = reader.read(file(withBom));

        assertThat(records.get(0).get("level")).isEqualTo("EASY");
        assertThat(records.get(0).get("questionContent")).isEqualTo("Trận Bạch Đằng");
    }

    @Test
    void rejectsWindows1252Bytes() {
        byte[] ansi = (HEADER + "EASY,Lê Lợi\n").getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> reader.read(file(ansi)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("CSV UTF-8");
    }

    @Test
    void rejectsMojibakeText() {
        assertThatThrownBy(() -> reader.read(file(utf8(HEADER + "EASY,Tráº­n Báº¡ch Äáº±ng\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Câu hỏi 1")
                .hasMessageContaining("lỗi font");
    }

    @Test
    void acceptsLegitVietnamese() {
        String rows = "EASY,NGÃ BA ĐỒNG LỘC Ngô Quyền\n"
                + "EASY,Điện Biên Phủ Đ\n"
                + "EASY,Ngã ba Ã\n";

        assertThatCode(() -> reader.read(file(utf8(HEADER + rows)))).doesNotThrowAnyException();
    }

    @Test
    void semicolonDelimitedFileGivesHint() {
        assertThatThrownBy(() -> reader.read(file(utf8("level;questionContent\nEASY;Q\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("dấu phẩy");
    }

    @Test
    void missingQuestionContentHeaderFails() {
        assertThatThrownBy(() -> reader.read(file(utf8("level\nEASY\n"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("File CSV thiếu cột tiêu đề bắt buộc: 'questionContent'");
    }

    @Test
    void missingExplanationHeaderIsAllowed() {
        assertThatCode(() -> reader.read(file(utf8(HEADER + "EASY,Q\n")))).doesNotThrowAnyException();
    }

    @Test
    void trailingEmptyHeaderColumnsFromExcelAreAllowed() {
        List<CSVRecord> records = reader.read(file(utf8("level,questionContent,,\nEASY,Q,,\n")));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("questionContent")).isEqualTo("Q");
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
