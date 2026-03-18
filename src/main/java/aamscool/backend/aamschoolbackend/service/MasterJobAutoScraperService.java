package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.model.Category;
import aamscool.backend.aamschoolbackend.model.Post;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.util.HomepageScraperService;
import aamscool.backend.aamschoolbackend.util.LabelUtil;

@Service
public class MasterJobAutoScraperService {

    private static final Logger log = LoggerFactory.getLogger(MasterJobAutoScraperService.class);
    private static final int DEFAULT_HOMEPAGE_SCRAPE_LIMIT = 5;

    private final MasterJobScraperService masterJobScraperService;
    private final JobMasterService jobMasterService;
    private final ScrapeCache cache;

    public MasterJobAutoScraperService(MasterJobScraperService masterJobScraperService,
                                       JobMasterService jobMasterService,
                                       ScrapeCache cache) {
        this.masterJobScraperService = masterJobScraperService;
        this.jobMasterService = jobMasterService;
        this.cache = cache;
    }

    public Map<String, Object> scrapeHomepageAndSave(boolean force) {
        return scrapeHomepageAndSave(force, DEFAULT_HOMEPAGE_SCRAPE_LIMIT, 1);
    }

    public Map<String, Object> scrapeHomepageAndSave(boolean force, int maxLinks) {
        int effectiveMaxLinks = maxLinks <= 0 ? DEFAULT_HOMEPAGE_SCRAPE_LIMIT : maxLinks;
        return scrapeHomepageAndSave(force, effectiveMaxLinks, 1);
    }

    public Map<String, Object> scrapeHomepageAndSave(boolean force, int maxLinks, int maxPerCategory) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> categorySummaries = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();

        int totalLinks = 0;
        int saved = 0;
        int skipped = 0;

        List<Category> categories;
        try {
            categories = HomepageScraperService.scrapeHomepage();
        } catch (IOException ex) {
            summary.put("status", "error");
            summary.put("message", "Failed to scrape homepage categories");
            summary.put("details", ex.getMessage());
            return summary;
        }

        int processedLinks = 0;
        int safeMaxLinks = maxLinks <= 0 ? DEFAULT_HOMEPAGE_SCRAPE_LIMIT : maxLinks;
        int safeMaxPerCategory = maxPerCategory <= 0 ? Integer.MAX_VALUE : maxPerCategory;

        for (Category category : categories) {
            String label = LabelUtil.normalizeCategoryLabel(category.getName());
            int categorySaved = 0;
            int categorySkipped = 0;
            int categoryProcessed = 0;

            for (Post post : category.getPosts()) {
                if (processedLinks >= safeMaxLinks || categoryProcessed >= safeMaxPerCategory) {
                    break;
                }
                String url = post.getUrl();
                if (url == null || url.isBlank()) {
                    continue;
                }
                totalLinks++;

                if (cache.isProcessed(url)) {
                    skipped++;
                    categorySkipped++;
                    processedLinks++;
                    categoryProcessed++;
                    continue;
                }

                try {
                    MasterJobResponseDto dto = masterJobScraperService.scrapeToMasterJson(url, true, false);
                    saveDto(dto, label);
                    cache.markProcessed(url);
                    saved++;
                    categorySaved++;
                    processedLinks++;
                    categoryProcessed++;
                } catch (Exception ex) {
                    Map<String, String> err = new LinkedHashMap<>();
                    err.put("category", category.getName());
                    err.put("url", url);
                    err.put("error", ex.getMessage());
                    errors.add(err);
                    log.error("Failed to scrape/save {}", url, ex);
                    processedLinks++;
                    categoryProcessed++;
                }
            }

            if (processedLinks >= safeMaxLinks) {
                Map<String, Object> cs = new LinkedHashMap<>();
                cs.put("category", category.getName());
                cs.put("label", label);
                cs.put("total_links", category.getPosts().size());
                cs.put("saved", categorySaved);
                cs.put("skipped", categorySkipped);
                categorySummaries.add(cs);
                break;
            }

            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("category", category.getName());
            cs.put("label", label);
            cs.put("total_links", category.getPosts().size());
            cs.put("saved", categorySaved);
            cs.put("skipped", categorySkipped);
            categorySummaries.add(cs);
        }

        summary.put("status", "success");
        summary.put("total_links", totalLinks);
        summary.put("saved", saved);
        summary.put("skipped", skipped);
        summary.put("failed", errors.size());
        summary.put("max_links", safeMaxLinks == Integer.MAX_VALUE ? "unlimited" : safeMaxLinks);
        summary.put("max_per_category", safeMaxPerCategory == Integer.MAX_VALUE ? "unlimited" : safeMaxPerCategory);
        summary.put("processed_links", processedLinks);
        summary.put("categories", categorySummaries);
        summary.put("errors", errors);
        return summary;
    }

    public Map<String, Object> scrapeSingleAndSave(String url, String label, boolean force, boolean includeLegacyPostWise) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (url == null || url.isBlank()) {
            resp.put("status", "error");
            resp.put("message", "url is required");
            return resp;
        }

        String normalizedLabel = LabelUtil.normalizeCategoryLabel(label);
        if (!force && cache.isProcessed(url)) {
            resp.put("status", "skipped");
            resp.put("message", "Already processed");
            resp.put("url", url);
            return resp;
        }

        try {
            MasterJobResponseDto dto = masterJobScraperService.scrapeToMasterJson(url, true, includeLegacyPostWise);
            saveDto(dto, normalizedLabel);
            cache.markProcessed(url);
            resp.put("status", "success");
            resp.put("url", url);
            resp.put("label", normalizedLabel);
            resp.put("title", dto.getTitle());
            return resp;
        } catch (Exception ex) {
            resp.put("status", "error");
            resp.put("url", url);
            resp.put("message", ex.getMessage());
            return resp;
        }
    }

    private void saveDto(MasterJobResponseDto dto, String label) throws Exception {
        if (dto == null) {
            return;
        }
        String safeLabel = (label == null || label.isBlank()) ? "job" : label;
        jobMasterService.saveOrUpdate(dto, safeLabel);
    }
}
