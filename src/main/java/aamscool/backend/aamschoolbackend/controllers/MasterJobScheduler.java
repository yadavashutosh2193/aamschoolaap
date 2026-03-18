package aamscool.backend.aamschoolbackend.controllers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.service.MasterJobAutoScraperService;

@Component
public class MasterJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(MasterJobScheduler.class);

    private final MasterJobAutoScraperService masterJobAutoScraperService;
    private final ScrapeCache scrapeCache;

    public MasterJobScheduler(MasterJobAutoScraperService masterJobAutoScraperService,
                              ScrapeCache scrapeCache) {
        this.masterJobAutoScraperService = masterJobAutoScraperService;
        this.scrapeCache = scrapeCache;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Kolkata")
    public void runHourlyHomepageScrape() {
        Map<String, Object> result = runScheduler(false);
        if ("error".equals(result.get("status"))) {
            log.error("Master job hourly scheduler failed: {}", result);
            return;
        }
        log.info("Master job hourly scheduler completed: {}", result);
    }

    public Map<String, Object> runNowWithCacheInvalidation() {
        return runScheduler(true);
    }

    private Map<String, Object> runScheduler(boolean invalidateCacheFirst) {
        if (invalidateCacheFirst) {
            scrapeCache.invalidateLinkCaches();
        }

        Map<String, Object> summary =
                masterJobAutoScraperService.scrapeHomepageAndSave(false, Integer.MAX_VALUE, Integer.MAX_VALUE);
        summary.put("trigger", invalidateCacheFirst ? "manual" : "scheduled");
        summary.put("cache_invalidated", invalidateCacheFirst);
        return summary;
    }
}
