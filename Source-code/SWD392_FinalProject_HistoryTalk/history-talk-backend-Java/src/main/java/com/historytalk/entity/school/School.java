package com.historytalk.entity.school;

import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.user.User;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "school")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class School {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "school_code", length = 50, nullable = false, unique = true)
    private String schoolCode;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "contact_email", length = 100, nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", length = 50, nullable = false)
    private EnterprisePackage packageType;

    @Builder.Default
    @Column(name = "total_school_token_quota", nullable = false)
    private Integer totalSchoolTokenQuota = 0;

    @Builder.Default
    @Column(name = "unallocated_token_quota", nullable = false)
    private Integer unallocatedTokenQuota = 0;

    @Builder.Default
    @Column(name = "local_history_policy_accepted", nullable = false)
    private Boolean localHistoryPolicyAccepted = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SchoolStatus status = SchoolStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "school", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();
}
