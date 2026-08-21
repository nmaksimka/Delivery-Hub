package com.deliveryhub.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    public static final String SIMULATOR_SCHEDULER = "simulatorScheduler";

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("scheduling-", 4);
    }

    @Bean(SIMULATOR_SCHEDULER)
    public ThreadPoolTaskScheduler simulatorScheduler() {
        return scheduler("simulator-", 1);
    }

    private static ThreadPoolTaskScheduler scheduler(String threadNamePrefix, int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
