package com.historytalk.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps open-in-view for the whole API except chat. Open-in-view holds a database connection for the
 * entire request, and chat requests wait seconds to minutes for the LLM while the pool is small.
 * Chat services therefore open their own short transactions around database work.
 */
@Configuration
@RequiredArgsConstructor
public class ChatOpenInViewConfig implements WebMvcConfigurer {

    private final ObjectProvider<EntityManagerFactory> entityManagerFactory;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        entityManagerFactory.ifAvailable(factory -> {
            OpenEntityManagerInViewInterceptor interceptor = new OpenEntityManagerInViewInterceptor();
            interceptor.setEntityManagerFactory(factory);
            registry.addWebRequestInterceptor(interceptor).excludePathPatterns("/api/v1/chat/**");
        });
    }
}
