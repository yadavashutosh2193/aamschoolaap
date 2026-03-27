package aamscool.backend.aamschoolbackend.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import aamscool.backend.aamschoolbackend.service.CurrentAffairsQuizService;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CurrentAffairsQuizScheduler {

    private static final Logger log = LoggerFactory.getLogger(CurrentAffairsQuizScheduler.class);
    private final CurrentAffairsQuizService quizService;
    private final AtomicBoolean schedulerEnabled;

    public CurrentAffairsQuizScheduler(CurrentAffairsQuizService quizService) {
        this.quizService = quizService;
        this.schedulerEnabled = new AtomicBoolean(false);
        log.info("Current affairs quiz scheduler initial state: DISABLED");
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
    public void runDailyQuizGeneration() {
        if (!schedulerEnabled.get()) {
            log.info("Current affairs quiz scheduler is disabled. Skipping scheduled run.");
            return;
        }
        try {
            log.info("Current affairs quiz generation started");
            quizService.generateAndSaveTodayQuiz();
            log.info("Current affairs quiz generation finished");
        } catch (Exception ex) {
            log.error("Current affairs quiz generation failed", ex);
        }
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled.get();
    }

    public boolean setSchedulerEnabled(boolean enabled) {
        schedulerEnabled.set(enabled);
        log.info("Current affairs quiz scheduler state changed to {}", enabled ? "ENABLED" : "DISABLED");
        return schedulerEnabled.get();
    }
}
