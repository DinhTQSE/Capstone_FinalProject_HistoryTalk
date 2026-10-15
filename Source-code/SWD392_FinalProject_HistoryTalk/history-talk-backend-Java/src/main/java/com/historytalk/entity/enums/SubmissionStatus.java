package com.historytalk.entity.enums;

public enum SubmissionStatus {
    NOT_STARTED,    // Mặc định: HS chưa mở bài
    IN_PROGRESS,    // Đang làm: Đã mở bài / đang chat / đang làm quiz
    SUBMITTED,      // Đã nộp đúng hạn (submitted_at <= deadline)
    LATE_SUBMITTED, // Nộp trễ (submitted_at > deadline)
    MISSED,         // Quá hạn mà chưa bấm nộp bài
    EXEMPTED        // Miễn làm (Học sinh chuyển vào sau khi bài đã giao)
}
