package com.historytalk.service.quiz.csv;

import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an uploaded quiz CSV into records, guarding against the encoding problems
 * that appear when the file is opened and saved with Excel.
 */
@Slf4j
@Component
public class QuizCsvFileReader {

    public static final List<String> REQUIRED_HEADERS = List.of("title", "contextId", "level", "questionContent");

    /**
     * UTF-8 Vietnamese text that was decoded as Windows-1252 (e.g. "Tráº­n Báº¡ch Äáº±ng").
     * These sequences never occur in correctly encoded Vietnamese.
     */
    private static final Pattern MOJIBAKE = Pattern.compile("Ã[\\u0080-\\u00BF]|á[º»]|Æ[°¡]|Ä[\\u0090\\u2018\\u0192]");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    public List<CSVRecord> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File CSV không được để trống");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".csv")) {
            throw new InvalidRequestException("Chỉ chấp nhận file .csv");
        }

        String content = decodeUtf8(stripBom(readBytes(file)));

        List<CSVRecord> records;
        try (CSVParser parser = FORMAT.parse(new StringReader(content))) {
            validateHeaders(parser.getHeaderMap());
            records = parser.getRecords();
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            log.warn("Malformed quiz CSV: {}", e.getMessage());
            throw new InvalidRequestException("File CSV sai định dạng: " + e.getMessage());
        }

        if (records.isEmpty()) {
            throw new InvalidRequestException("File CSV không chứa hàng dữ liệu hợp lệ nào");
        }
        for (CSVRecord record : records) {
            rejectMojibake(record);
        }
        return records;
    }

    /** Human-facing line number: the header is line 1, the first data row is line 2. */
    public static String lineLabel(CSVRecord row) {
        return "Dòng " + (row.getRecordNumber() + 1);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read CSV file", e);
            throw new SystemException("Không thể đọc file CSV: " + e.getMessage());
        }
    }

    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            byte[] stripped = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidRequestException("File CSV không phải UTF-8. Trong Excel chọn File > Save As > "
                    + "'CSV UTF-8 (Comma delimited) (*.csv)'");
        }
    }

    private static void validateHeaders(Map<String, Integer> headerMap) {
        for (String header : REQUIRED_HEADERS) {
            if (!headerMap.containsKey(header)) {
                String message = "File CSV thiếu cột tiêu đề bắt buộc: '" + header + "'";
                if (headerMap.size() == 1 && headerMap.keySet().iterator().next().contains(";")) {
                    message += ". File có vẻ dùng dấu ';' — hãy lưu CSV với dấu phẩy (,)";
                }
                throw new InvalidRequestException(message);
            }
        }
    }

    private static void rejectMojibake(CSVRecord record) {
        for (String value : record) {
            if (value == null) {
                continue;
            }
            Matcher matcher = MOJIBAKE.matcher(value);
            if (matcher.find()) {
                int start = Math.max(0, matcher.start() - 5);
                int end = Math.min(value.length(), matcher.end() + 5);
                throw new InvalidRequestException(lineLabel(record) + ": phát hiện ký tự lỗi font ('"
                        + value.substring(start, end) + "'). File đã bị Excel đọc sai bảng mã — tải lại file mẫu "
                        + "và nhập lại bằng 'CSV UTF-8'");
            }
        }
    }
}
