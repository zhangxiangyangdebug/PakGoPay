package com.pakgopay.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebAsyncConfig implements WebMvcConfigurer {
    @Bean(name = "pakGoPayExecutor")
    public ThreadPoolTaskExecutor mvcIoExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(50);
        ex.setMaxPoolSize(200);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("mvc-io-");
        ex.initialize();
        return ex;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcIoExecutor());
        configurer.setDefaultTimeout(320000L);
    }
}
