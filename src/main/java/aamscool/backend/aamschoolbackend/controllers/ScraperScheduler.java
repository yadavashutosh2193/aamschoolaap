package aamscool.backend.aamschoolbackend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import aamscool.backend.aamschoolbackend.service.ScraperService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ScraperScheduler {

    @Autowired
    private ScraperService scraperService;

    @Scheduled(cron = "0 0 */2 * * *")
    public void autoRun() {

        //log.info("Auto Scraper Started");

        scraperService.runScraper();

        //log.info("Auto Scraper Finished");
    }
}

