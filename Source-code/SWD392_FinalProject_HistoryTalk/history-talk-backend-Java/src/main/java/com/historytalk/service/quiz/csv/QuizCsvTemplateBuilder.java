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
 * Vietnamese text correctly, '#' guidance lines, the header, and one example row per
 * question type. The quiz title and context are chosen on the upload form, so the
 * file holds question rows only.
 *
 * <p>The guidance text mirrors {@code docs/sprints/sprint_6/business_flows/quiz/quiz-import-sample.csv}.
 * It must not contain double quotes: Excel would wrap such a line in quotes on save,
 * and it would then no longer start with '#'.
 */
@Component
public class QuizCsvTemplateBuilder {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setCommentMarker('#')
            .build();

    private static final List<String> GUIDANCE = List.of(
            "===== HƯỚNG DẪN NHẬP CÂU HỎI QUIZ BẰNG FILE CSV =====",
            "Các dòng bắt đầu bằng dấu # là ghi chú: hệ thống tự bỏ qua khi import. Có thể giữ nguyên hoặc xoá đi.",
            "Tên quiz và bối cảnh lịch sử được chọn trên màn hình import - KHÔNG ghi vào file. Mỗi file là 1 quiz; mỗi dòng dữ liệu là 1 câu hỏi.",
            "Cách lưu file trong Excel: File > Save As > CSV UTF-8 (Comma delimited) (*.csv). Không lưu bằng dấu ; làm dấu phân cách.",
            "Không đổi tên và không xoá dòng tiêu đề cột (dòng bắt đầu bằng chữ level - nằm ngay dưới phần ghi chú).",
            "",
            "----- Ý NGHĨA CÁC CỘT -----",
            "level (bắt buộc): độ khó của câu hỏi - EASY / MEDIUM / HARD",
            "questionType (không bắt buộc): MULTIPLE_CHOICE / FILL_IN_BLANK / MATCHING. Để trống = MULTIPLE_CHOICE",
            "questionContent (bắt buộc): nội dung câu hỏi",
            "option1 .. option6: các lựa chọn của câu hỏi. Điền liền nhau từ option1 - tối thiểu 2 và tối đa 6 - không để trống ô ở giữa",
            "correctAnswer: đáp án đúng. Cách ghi tuỳ loại câu hỏi (xem phần bên dưới)",
            "caseSensitive / trimWhitespace: chỉ dùng cho FILL_IN_BLANK; các loại khác để trống",
            "explanation (không bắt buộc): lời giải thích hiển thị sau khi học sinh trả lời",
            "",
            "----- CÁCH GHI THEO TỪNG LOẠI CÂU HỎI -----",
            "MULTIPLE_CHOICE (trắc nghiệm): mỗi ô option là 1 lựa chọn.",
            "  correctAnswer = SỐ THỨ TỰ của đáp án đúng và ĐẾM TỪ 0: option1 = 0 / option2 = 1 / option3 = 2 / option4 = 3 / option5 = 4 / option6 = 5",
            "  Nhiều đáp án đúng thì ngăn cách bằng dấu | . Ví dụ 0|1|3 nghĩa là option1 + option2 + option4 đều đúng",
            "FILL_IN_BLANK (điền vào chỗ trống): để trống toàn bộ option1 .. option6.",
            "  correctAnswer = các đáp án được chấp nhận ngăn cách bằng dấu | . Ví dụ 07/05/1954|7/5/1954",
            "  caseSensitive: true = phân biệt chữ hoa chữ thường; để trống = false",
            "  trimWhitespace: true = bỏ qua khoảng trắng thừa ở đầu và cuối câu trả lời; để trống = true",
            "MATCHING (nối cặp): mỗi ô option là 1 cặp theo dạng   Vế trái => Vế phải",
            "  Để trống correctAnswer: các cặp ghi trong option chính là đáp án đúng. Hệ thống tự xáo trộn vế phải khi hiển thị",
            "  Không được lặp lại cùng một vế trái hoặc cùng một vế phải trong một câu hỏi",
            "",
            "----- LƯU Ý -----",
            "Nếu có câu hỏi sai định dạng thì hệ thống báo lỗi kèm số thứ tự câu hỏi và KHÔNG lưu câu nào. Hãy sửa file rồi import lại.",
            "Nội dung có dấu phẩy phải nằm trong ngoặc kép - Excel tự làm việc này khi lưu file. Không dùng dấu ngoặc kép trong dòng ghi chú.",
            "Quiz sau khi import ở trạng thái nháp (chưa xuất bản). Hãy kiểm tra lại câu hỏi rồi mới xuất bản.",
            "Các dòng dữ liệu bên dưới là ví dụ - hãy thay bằng câu hỏi của bạn.");

    private static final List<String> HEADER = List.of(
            "level", "questionType", "questionContent",
            "option1", "option2", "option3", "option4", "option5", "option6",
            "correctAnswer", "caseSensitive", "trimWhitespace", "explanation");

    private static final List<List<String>> SAMPLE_ROWS = List.of(
            List.of("EASY", "MULTIPLE_CHOICE",
                    "Ai là người chỉ huy quân ta trong trận Bạch Đằng năm 938?",
                    "Ngô Quyền", "Lê Hoàn", "Trần Hưng Đạo", "Lý Thường Kiệt", "", "",
                    "0", "", "",
                    "Ngô Quyền cho cắm cọc gỗ trên sông Bạch Đằng, đánh tan quân Nam Hán."),
            List.of("MEDIUM", "FILL_IN_BLANK",
                    "Trận Bạch Đằng do Ngô Quyền chỉ huy diễn ra vào năm nào?",
                    "", "", "", "", "", "",
                    "938|năm 938", "false", "true",
                    "Chiến thắng năm 938 chấm dứt hơn một nghìn năm Bắc thuộc."),
            List.of("HARD", "MATCHING",
                    "Nối nhân vật lịch sử với trận đánh tương ứng",
                    "Ngô Quyền => Bạch Đằng 938", "Lê Lợi => Chi Lăng 1427",
                    "Quang Trung => Ngọc Hồi - Đống Đa 1789", "", "", "",
                    "", "", "",
                    ""));

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, FORMAT)) {
            for (String line : GUIDANCE) {
                printer.printComment(line);
            }
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
