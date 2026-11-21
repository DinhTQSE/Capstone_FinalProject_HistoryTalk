package com.historytalk.dto.localcontent;

/**
 * A published local story that shows a given character. Hiding, deleting or unlinking that character moves the
 * story back to draft, so the FE lists these in its warning first.
 */
public record LocalStoryUsageResponse(String contextId, String contextName, String storyTitle) {
}
