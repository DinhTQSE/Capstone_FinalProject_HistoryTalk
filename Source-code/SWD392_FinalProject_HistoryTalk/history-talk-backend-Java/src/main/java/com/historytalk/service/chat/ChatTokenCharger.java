package com.historytalk.service.chat;

import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.school.token.DailyTokenAllowanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatTokenCharger {

    private final UserRepository userRepository;
    private final DailyTokenAllowanceService dailyTokenAllowanceService;

    /** Charges a managed user for an AI reply and returns the tokens they have left. */
    public int charge(User user, int totalTokens) {
        int remainingTokens = user.getToken() != null ? user.getToken() : 0;
        if (totalTokens <= 0) {
            return remainingTokens;
        }
        if (user.getRole() == UserRole.CUSTOMER) {
            userRepository.deductTokens(user.getUid(), totalTokens);
            remainingTokens = Math.max(0, remainingTokens - totalTokens);
            user.setToken(remainingTokens);
        } else if (user.getRole() == UserRole.SCHOOL_STUDENT || user.getRole() == UserRole.TEACHER) {
            remainingTokens = dailyTokenAllowanceService.recordStudentTokenUsage(user, totalTokens);
        }
        return remainingTokens;
    }
}
