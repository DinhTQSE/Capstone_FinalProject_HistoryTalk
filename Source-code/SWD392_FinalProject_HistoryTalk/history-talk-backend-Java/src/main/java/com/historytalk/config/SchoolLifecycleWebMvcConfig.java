package com.historytalk.config;

import com.historytalk.security.SchoolSubscriptionGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SchoolLifecycleWebMvcConfig implements WebMvcConfigurer {

    private final SchoolSubscriptionGuardInterceptor schoolSubscriptionGuardInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(schoolSubscriptionGuardInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/admin/**"
                );
    }
}
