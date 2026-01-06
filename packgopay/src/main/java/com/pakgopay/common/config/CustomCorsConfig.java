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
