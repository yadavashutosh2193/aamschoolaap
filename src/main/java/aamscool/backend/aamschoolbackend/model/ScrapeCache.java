package aamscool.backend.aamschoolbackend.model;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.util.LabelUtil;

@Component
public class ScrapeCache {

	private static final Logger log = LoggerFactory.getLogger(ScrapeCache.class);
    public static Cache<String, Boolean> processedLinks = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
   public static Cache<String, List<HomePageLinksModel>> dataCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .build();
   public static Cache<Long, JobPosts> jsondata = Caffeine.newBuilder()
           .maximumSize(1000)
           .build();
   public static Cache<String, List<HomePageLinksModel>> masterDataCache = Caffeine.newBuilder()
           .maximumSize(1000)
           .build();
   public static Cache<Long, MasterJobResponseDto> masterJsondata = Caffeine.newBuilder()
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

    public void invalidateProcessedLinks() {
        processedLinks.invalidateAll();
        log.info("Invalidated processed links cache");
    }

    public void invalidateHomepageDataCache() {
        dataCache.invalidateAll();
        masterDataCache.invalidateAll();
        log.info("Invalidated homepage links data cache");
    }

    public void invalidateLinkCaches() {
        invalidateProcessedLinks();
        invalidateHomepageDataCache();
    }

    public String normalizeLabelKey(String label) {
        return LabelUtil.normalizeCategoryLabel(label);
    }

    public void invalidateJobsLabel(String label) {
        for (String key : LabelUtil.buildLabelLookupCandidates(label)) {
            dataCache.invalidate(key);
            dataCache.invalidate(normalizeLabelKey(key));
        }
        dataCache.invalidate(normalizeLabelKey(label));
    }

    public void invalidateMasterJobsLabel(String label) {
        for (String key : LabelUtil.buildLabelLookupCandidates(label)) {
            masterDataCache.invalidate(key);
            masterDataCache.invalidate(normalizeLabelKey(key));
        }
        masterDataCache.invalidate(normalizeLabelKey(label));
    }

    public void putJobPost(JobPosts post) {
        if (post == null) {
            return;
        }
        jsondata.put(post.getJobId(), post);
    }

    public void invalidateJobPost(long id) {
        jsondata.invalidate(id);
    }

    public void putMasterJob(long id, MasterJobResponseDto dto) {
        if (dto == null || id <= 0) {
            return;
        }
        masterJsondata.put(id, dto);
    }

    public void invalidateMasterJob(long id) {
        masterJsondata.invalidate(id);
    }
}

