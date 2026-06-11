package com.ballot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Registers a dedicated TaskScheduler bean used by DynamicBallotScheduler.
 *
 * Why a separate bean from AsyncConfig's taskExecutor?
 *   - taskExecutor  → fire-and-forget async tasks (@Async, emails, audit logs)
 *   - taskScheduler → time-triggered tasks (schedule at exact Instant)
 * Mixing them would let long-running scheduled tasks starve the async pool.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 4 threads: enough for activate + close + maintenance jobs running concurrently
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ballot-scheduler-");
        // Log instead of swallowing uncaught exceptions from scheduled tasks
        scheduler.setErrorHandler(t ->
                org.slf4j.LoggerFactory.getLogger(SchedulerConfig.class)
                        .error("[SCHEDULER] Uncaught error in scheduled task", t));
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
