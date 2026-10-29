package com.historytalk.service.quiz.csv;

import com.historytalk.exception.SystemException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the downloadable quiz import template: UTF-8 with a BOM so Excel opens
 * Vietnamese text correctly, one example row per question type.
 */
@Component
public class QuizCsvTemplateBuilder {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final List<String> HEADER = List.of(
            "title", "contextId", "level", "questionType", "questionContent",
            "option1", "option2", "option3", "option4", "option5", "option6",
            "correctAnswer", "caseSensitive", "trimWhitespace", "explanation");

    /** Placeholder — staff must replace it with a real historical context id. */
    private static final String SAMPLE_CONTEXT_ID = "00000000-0000-0000-0000-000000000000";
    private static final String SAMPLE_TITLE = "Trận Bạch Đằng 938";

    private static final List<List<String>> SAMPLE_ROWS = List.of(
            List.of(SAMPLE_TITLE, SAMPLE_CONTEXT_ID, "EASY", "MULTIPLE_CHOICE",
                    "Ai là người chỉ huy quân ta trong trận Bạch Đằng năm 938?",
                    "Ngô Quyền", "Lê Hoàn", "Trần Hưng Đạo", "Lý Thường Kiệt", "", "",
                    "0", "", "",
                    "Ngô Quyền cho cắm cọc gỗ trên sông Bạch Đằng, đánh tan quân Nam Hán."),
            List.of(SAMPLE_TITLE, SAMPLE_CONTEXT_ID, "EASY", "FILL_IN_BLANK",
                    "Trận Bạch Đằng do Ngô Quyền chỉ huy diễn ra vào năm nào?",
                    "", "", "", "", "", "",
                    "938|năm 938", "false", "true",
                    "Chiến thắng năm 938 chấm dứt hơn một nghìn năm Bắc thuộc."),
            List.of(SAMPLE_TITLE, SAMPLE_CONTEXT_ID, "EASY", "MATCHING",
                    "Nối nhân vật lịch sử với trận đánh tương ứng",
                    "Ngô Quyền => Bạch Đằng 938", "Lê Lợi => Chi Lăng 1427",
                    "Quang Trung => Ngọc Hồi - Đống Đa 1789", "", "", "",
                    "", "", "",
                    ""));

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord(HEADER);
            for (List<String> row : SAMPLE_ROWS) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new SystemException("Không thể tạo file CSV mẫu: " + e.getMessage());
        }
        return out.toByteArray();
    }
}
