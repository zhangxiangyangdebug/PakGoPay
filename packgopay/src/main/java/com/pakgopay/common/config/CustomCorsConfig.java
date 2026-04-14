package com.pakgopay.common.config;

import com.pakgopay.filter.PakGoPayInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableWebMvc
public class CustomCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                /*.allowedOrigins("http://localhost:5173");*/
                .allowedOriginPatterns("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration registration = registry.addInterceptor(new PakGoPayInterceptor());
        registration.addPathPatterns("/**");
    }

    @Bean(name = "createOrderApiAsyncExecutor")
    public ThreadPoolTaskExecutor createOrderApiAsyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(50);
        ex.setMaxPoolSize(200);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("create-api-async-");
        ex.setTaskDecorator(new MdcTaskDecorator());
        ex.initialize();
        return ex;
    }

    @Bean(name = "notifyApiAsyncExecutor")
    public ThreadPoolTaskExecutor notifyApiAsyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(50);
        ex.setMaxPoolSize(200);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("notify-api-async-");
        ex.setTaskDecorator(new MdcTaskDecorator());
        ex.initialize();
        return ex;
    }

    @Bean(name = "orderBgExecutor")
    public ThreadPoolTaskExecutor orderBgExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(2000);
        ex.setThreadNamePrefix("order-bg-");
        ex.setTaskDecorator(new MdcTaskDecorator());
        ex.initialize();
        return ex;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(createOrderApiAsyncExecutor());
        configurer.setDefaultTimeout(320000L);
    }
}
