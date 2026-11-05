package com.gestao.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
@Configuration
public class EmailAsyncConfig {
    @Bean("emailExecutor")
    public Executor emailExecutor() {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(2); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-recuperacao-");
        executor.initialize();
        return executor;
    }
}
