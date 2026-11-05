package com.historytalk.entity.enums;

/**
 * How a chat session behaves.
 * <ul>
 *   <li>{@code CHAT} – the learner asks, the character answers.</li>
 *   <li>{@code EXAM} – the character asks, grades each answer and scores the session.</li>
 * </ul>
 */
public enum ChatSessionMode {
    CHAT,
    EXAM
}
