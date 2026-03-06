package com.historyTalk.entity;

import com.historyTalk.entity.staff.Staff;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "role_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID roleId;

    @Column(name = "role_name", length = 50, nullable = false, unique = true)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;

    @Builder.Default
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private List<Staff> staffMembers = new ArrayList<>();

}
