package com.historytalk.entity.enums;

/**
 * Visibility scope for local (school-owned) content.
 * <ul>
 *   <li>{@code CLASS_ONLY}  – visible only to the creating teacher's class(es).</li>
 *   <li>{@code SCHOOL_WIDE} – visible to all teachers in the same school (they can adopt it
 *       into their own classes after School Admin publishes it).</li>
 * </ul>
 * Global platform content (school_id = NULL) does not use this enum.
 */
public enum ContentScope {
    CLASS_ONLY,
    SCHOOL_WIDE
}
