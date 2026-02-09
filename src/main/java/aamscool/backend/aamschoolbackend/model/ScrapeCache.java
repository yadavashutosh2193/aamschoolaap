package aamscool.backend.aamschoolbackend.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ScrapeCache {

    private final Set<String> processedLinks =
        ConcurrentHashMap.newKeySet();

    public boolean isProcessed(String link) {
        return processedLinks.contains(link);
    }

    public void markProcessed(String link) {
        processedLinks.add(link);
    }
}

