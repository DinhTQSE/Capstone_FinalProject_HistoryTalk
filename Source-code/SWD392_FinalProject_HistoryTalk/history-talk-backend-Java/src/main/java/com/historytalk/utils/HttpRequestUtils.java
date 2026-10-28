package com.historytalk.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public class HttpRequestUtils {

    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "Unknown IP";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip.trim())) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip.trim())) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    public static String getDeviceInfo(HttpServletRequest request) {
        if (request == null) {
            return "Unknown Device";
        }
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return "Unknown Device";
        }
        String os = "Unknown OS";
        if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS")) {
            os = "macOS";
        } else if (userAgent.contains("iPhone")) {
            os = "iOS (iPhone)";
        } else if (userAgent.contains("iPad")) {
            os = "iPadOS";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        }

        String browser = "Browser";
        if (userAgent.contains("Edg")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome")) {
            browser = "Chrome";
        } else if (userAgent.contains("Safari")) {
            browser = "Safari";
        } else if (userAgent.contains("Firefox")) {
            browser = "Firefox";
        }

        return browser + " / " + os;
    }
}
