package com.historytalk.repository.dashboard;

import java.util.UUID;

public interface StudentMessageCountProjection {
    UUID getStudentUid();
    Long getMessageCount();
}
