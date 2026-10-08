package com.historytalk.service.classroom;

import com.historytalk.repository.classroom.ClassroomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SmartClassCodeGenerator {

    private final ClassroomRepository classroomRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Sinh mã lớp học thông minh theo công thức:
     * {MÃ_TRƯỜNG}-{TÊN_LỚP_CHUẨN_HÓA}-{NĂM_HỌC}[-{HẬU_TỐ}]
     * Ví dụ: CVA-10A1-2026 hoặc CVA-10A1-2026-X89
     */
    public String generateClassCode(String schoolCode, String className, Integer academicYear) {
        String cleanSchool = (schoolCode != null ? schoolCode.trim().toUpperCase() : "SCH")
                .replaceAll("[^A-Z0-9]", "");

        String cleanClass = cleanClassName(className);
        int year = (academicYear != null) ? academicYear : 2026;

        String baseCode = String.format("%s-%s-%d", cleanSchool, cleanClass, year);

        if (!classroomRepository.existsByClassCodeIgnoreCase(baseCode)) {
            return baseCode;
        }

        // Nếu va chạm, sinh hậu tố ngẫu nhiên 3 ký tự chống trùng
        for (int i = 0; i < 20; i++) {
            String candidate = String.format("%s-%s", baseCode, generateRandomSuffix(3));
            if (!classroomRepository.existsByClassCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }

        return String.format("%s-%d", baseCode, System.currentTimeMillis() % 10000);
    }

    private String cleanClassName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "CLS";
        }

        // Bỏ dấu tiếng Việt
        String nfd = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccent = pattern.matcher(nfd).replaceAll("").replace("đ", "d").replace("Đ", "D");

        // Bỏ các từ tiền tố thừa như "lop", "class"
        String cleaned = noAccent.toUpperCase()
                .replaceAll("^LOP\\s*", "")
                .replaceAll("^CLASS\\s*", "")
                .replaceAll("[^A-Z0-9]", "");

        return cleaned.isEmpty() ? "CLS" : cleaned;
    }

    private String generateRandomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }
}
