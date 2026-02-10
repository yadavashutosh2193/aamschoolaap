package aamscool.backend.aamschoolbackend.model;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import aamscool.backend.aamschoolbackend.controllers.ScraperScheduler;

@Component
public class ScrapeCache {

	private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);
    Cache<String, Boolean> processedLinks = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
   public static Cache<String, List<HomePageLinksModel>> dataCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .build();
   public static Cache<Long, JobPosts> jsondata = Caffeine.newBuilder()
           .maximumSize(1000)
           .build();
 

    public boolean isProcessed(String link) {
		if (processedLinks.getIfPresent(link) != null)
			return true;
		else
			return false;
    }

    public void markProcessed(String link) {
    	log.info("added link into cache " + link);
        processedLinks.put(link, true);
    }
}

