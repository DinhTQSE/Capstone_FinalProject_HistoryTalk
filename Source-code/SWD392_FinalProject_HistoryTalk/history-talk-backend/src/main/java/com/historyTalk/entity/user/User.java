package com.historyTalk.entity.user;

import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.staff.Staff;
import com.historyTalk.entity.quiz.QuizResult;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "\"user\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "uid", length = 50)
    private String uid;

    @Column(name = "user_name", length = 100, nullable = false, unique = true)
    private String userName;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "password", length = 100, nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 50, nullable = false)
    private UserType userType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<ChatSession> chatSessions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<QuizResult> quizResults = new ArrayList<>();

    @PrePersist
    void ensureId() {
        if (this.uid == null) {
            this.uid = UUID.randomUUID().toString();
        }
    }
}
