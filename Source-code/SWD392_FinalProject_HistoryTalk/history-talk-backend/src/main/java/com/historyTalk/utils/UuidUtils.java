package com.historyTalk.utils;

import com.historyTalk.exception.InvalidRequestException;

import java.util.UUID;

/**
 * Utility for safe UUID conversion from String.
 * Throws InvalidRequestException on invalid UUID format instead of IllegalArgumentException.
 */
public class UuidUtils {

    private UuidUtils() {}

    /**
     * Safely convert a String to UUID.
     * If the string is not a valid UUID format, throws InvalidRequestException.
     *
     * @param uuidString the string to convert
     * @param fieldName the field name for error message
     * @return UUID object
     * @throws InvalidRequestException if UUID format is invalid
     */
    public static UUID fromString(String uuidString, String fieldName) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid " + fieldName + " format. Must be a valid UUID: " + uuidString);
        }
    }

    /**
     * Safely convert a String to UUID with default field name.
     *
     * @param uuidString the string to convert
     * @return UUID object
     * @throws InvalidRequestException if UUID format is invalid
     */
    public static UUID fromString(String uuidString) {
        return fromString(uuidString, "ID");
    }

}
