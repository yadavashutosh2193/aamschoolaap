package aamscool.backend.aamschoolbackend.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import aamscool.backend.aamschoolbackend.service.CurrentAffairsQuizService;

@Component
public class CurrentAffairsQuizScheduler {

    private static final Logger log = LoggerFactory.getLogger(CurrentAffairsQuizScheduler.class);

    @Autowired
    private CurrentAffairsQuizService quizService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
    public void runDailyQuizGeneration() {
        try {
            log.info("Current affairs quiz generation started");
            quizService.generateAndSaveTodayQuiz();
            log.info("Current affairs quiz generation finished");
        } catch (Exception ex) {
            log.error("Current affairs quiz generation failed", ex);
        }
    }
}
