package com.deliveryhub.restaurant.importer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties({ImportProperties.class, BootstrapProperties.class})
public class ImportConfig {

    @Bean
    public ImportInstance importInstance() {
        return ImportInstance.current();
    }

    @Bean
    public TaskExecutor importExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("catalog-import-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
