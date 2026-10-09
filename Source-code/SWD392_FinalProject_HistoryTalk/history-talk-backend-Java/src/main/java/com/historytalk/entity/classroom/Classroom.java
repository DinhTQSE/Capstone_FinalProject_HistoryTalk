package com.historytalk.entity.classroom;

import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "classroom")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classroom {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @Column(name = "class_name", length = 100, nullable = false)
    private String className;

    @Column(name = "class_code", length = 50, nullable = false, unique = true)
    private String classCode;

    @Column(name = "grade_level")
    private Integer gradeLevel;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "description", length = 500)
    private String description;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "classroom", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ClassStudent> classStudents = new ArrayList<>();
}
