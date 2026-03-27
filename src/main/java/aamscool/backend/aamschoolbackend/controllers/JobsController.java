package aamscool.backend.aamschoolbackend.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.service.JobsService;

@RestController
@RequestMapping("/api/jobs")
public class JobsController {

    private final JobsService jobsService;
    private final ScrapeCache scrapeCache;

    public JobsController(JobsService jobsService, ScrapeCache scrapeCache) {
        this.jobsService = jobsService;
        this.scrapeCache = scrapeCache;
    }

    @GetMapping("/latestjobs/{label}")
    public List<HomePageLinksModel> getLatestJob(@PathVariable("label") String label) {
        String cacheKey = scrapeCache.normalizeLabelKey(label);
        String requestedLabel = label;
        List<HomePageLinksModel> jobs = new ArrayList<HomePageLinksModel>();
        jobs = ScrapeCache.dataCache.get(cacheKey,
                key -> Optional.ofNullable(jobsService.getLatestJob(requestedLabel)).orElse(Collections.emptyList()));

        return jobs;
    }

    @GetMapping("/latestjobs-with-dates/{label}")
    public List<HomePageLinksModel> getLatestJobWithDates(@PathVariable("label") String label) {
        return getLatestJob(label);
    }

    @GetMapping("/jobbyid/{id}")
    public Optional<JobPosts> getPost(@PathVariable("id") long id) {
        JobPosts cached = ScrapeCache.jsondata.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<JobPosts> postOpt = jobsService.getPost(id);
        postOpt.ifPresent(post -> ScrapeCache.jsondata.put(id, post));
        return postOpt;
    }

    @GetMapping("/job/{slugWithId}")
    public Optional<JobPosts> getPost(@PathVariable("slugWithId") String slugWithId) {
        long id = extractIdFromSlug(slugWithId);
        if (id == -1) {
            return Optional.empty();
        }

        JobPosts cached = ScrapeCache.jsondata.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<JobPosts> postOpt = jobsService.getPost(id);
        postOpt.ifPresent(post -> ScrapeCache.jsondata.put(id, post));
        return postOpt;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable("id") long id) {
        boolean deleted = jobsService.deleteJob(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Job deleted"
        ));
    }

    private long extractIdFromSlug(String slug) {
        try {
            String idStr = slug.substring(slug.lastIndexOf("-") + 1);
            return Long.parseLong(idStr);
        } catch (Exception e) {
            return -1;
        }
    }
}
