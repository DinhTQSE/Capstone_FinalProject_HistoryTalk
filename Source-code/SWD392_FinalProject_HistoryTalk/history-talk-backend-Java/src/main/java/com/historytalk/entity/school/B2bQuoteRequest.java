package com.historytalk.entity.school;

import com.historytalk.entity.enums.B2bQuoteStatus;
import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "b2b_quote_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2bQuoteRequest {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "quote_code", length = 30, nullable = false, unique = true)
    private String quoteCode;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "contact_email", length = 150, nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30, nullable = false)
    private String contactPhone;

    @Column(name = "job_title", length = 150, nullable = false)
    private String jobTitle;

    @Column(name = "school_name", length = 255, nullable = false)
    private String schoolName;

    @Column(name = "school_code", length = 50)
    private String schoolCode;

    @Column(name = "school_address", length = 500)
    private String schoolAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_package", length = 50, nullable = false)
    private EnterprisePackage selectedPackage;

    @Column(name = "number_of_students", nullable = false)
    private Integer numberOfStudents;

    @Column(name = "number_of_teachers", nullable = false)
    private Integer numberOfTeachers;

    @Builder.Default
    @Column(name = "student_daily_token_limit", nullable = false)
    private Integer studentDailyTokenLimit = 5000;

    @Builder.Default
    @Column(name = "teacher_daily_token_limit", nullable = false)
    private Integer teacherDailyTokenLimit = 5000;

    @Column(name = "total_token_pool", nullable = false)
    private Long totalTokenPool;

    @Column(name = "ai_token_cost_vnd", nullable = false)
    private Long aiTokenCostVnd;

    @Column(name = "db_and_lms_cost_vnd", nullable = false)
    private Long dbAndLmsCostVnd;

    @Column(name = "subtotal_vnd", nullable = false)
    private Long subtotalVnd;

    @Builder.Default
    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent = 0;

    @Builder.Default
    @Column(name = "discount_amount_vnd", nullable = false)
    private Long discountAmountVnd = 0L;

    @Column(name = "final_price_vnd", nullable = false)
    private Long finalPriceVnd;

    @Column(name = "promo_code", length = 50)
    private String promoCode;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private B2bQuoteStatus status = B2bQuoteStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "reviewed_by_uid", columnDefinition = "uuid")
    private UUID reviewedByUid;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_school_id", columnDefinition = "uuid")
    private UUID createdSchoolId;

    @Column(name = "created_school_admin_uid", columnDefinition = "uuid")
    private UUID createdSchoolAdminUid;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
