package aamscool.backend.aamschoolbackend.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.service.MasterJobAutoScraperService;
import aamscool.backend.aamschoolbackend.service.JobMasterService;
import aamscool.backend.aamschoolbackend.service.MasterJobScraperService;
import aamscool.backend.aamschoolbackend.service.OpenAIService;

@RestController
@RequestMapping("/api/master-jobs")
public class MasterJobScraperController {

    private final MasterJobScraperService masterJobScraperService;
    private final MasterJobAutoScraperService masterJobAutoScraperService;
    private final JobMasterService jobMasterService;
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public MasterJobScraperController(MasterJobScraperService masterJobScraperService,
                                      MasterJobAutoScraperService masterJobAutoScraperService,
                                      JobMasterService jobMasterService,
                                      OpenAIService openAIService,
                                      ObjectMapper objectMapper) {
        this.masterJobScraperService = masterJobScraperService;
        this.masterJobAutoScraperService = masterJobAutoScraperService;
        this.jobMasterService = jobMasterService;
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/scrape")
    public ResponseEntity<?> scrape(@RequestParam String url,
                                    @RequestParam(defaultValue = "false") boolean includeLegacyPostWise) {
        try {
            MasterJobResponseDto response = masterJobScraperService.scrapeToMasterJson(url, true, includeLegacyPostWise);
            return ResponseEntity.ok(response);
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Failed to scrape URL",
                    "details", ex.getMessage()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Unexpected error during scraping",
                    "details", ex.getMessage()
            ));
        }
    }

    @PostMapping("/scrape-and-save")
    public ResponseEntity<?> scrapeAndSave(@RequestParam String url,
                                           @RequestParam(required = false) String label,
                                           @RequestParam(defaultValue = "false") boolean force,
                                           @RequestParam(defaultValue = "false") boolean includeLegacyPostWise) {
        Map<String, Object> resp =
                masterJobAutoScraperService.scrapeSingleAndSave(url, label, force, includeLegacyPostWise);
        Object status = resp.get("status");
        if ("error".equals(status)) {
            return ResponseEntity.badRequest().body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/auto-scrape-homepage")
    public ResponseEntity<?> autoScrapeHomepage(@RequestParam(defaultValue = "false") boolean force,
                                                @RequestParam(defaultValue = "0") int maxLinks,
                                                @RequestParam(defaultValue = "1") int maxPerCategory) {
        Map<String, Object> resp = masterJobAutoScraperService.scrapeHomepageAndSave(force, maxLinks, maxPerCategory);
        Object status = resp.get("status");
        if ("error".equals(status)) {
            return ResponseEntity.badRequest().body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/latest/{label}")
    public ResponseEntity<List<HomePageLinksModel>> latestByLabel(@PathVariable String label) {
        return ResponseEntity.ok(jobMasterService.getLatestByLabel(label));
    }

    @GetMapping("/job/{slugWithId}")
    public ResponseEntity<?> jobBySlug(@PathVariable String slugWithId) {
        long id = extractIdFromSlug(slugWithId);
        if (id <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Invalid slugWithId"
            ));
        }
        return jobMasterService.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jobbyid/{id}")
    public ResponseEntity<?> jobById(@PathVariable long id) {
        return jobMasterService.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/ai-direct")
    public ResponseEntity<?> aiDirect(@RequestParam String url) {
        try {
            String json = openAIService.generateMasterJsonFromUrlDirect(url);
            Map<String, Object> payload = objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {}
            );
            return ResponseEntity.ok(payload);
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Failed to process URL via OpenAI",
                    "details", ex.getMessage()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Unexpected error during AI direct extraction",
                    "details", ex.getMessage()
            ));
        }
    }

    private long extractIdFromSlug(String slug) {
        try {
            String idStr = slug.substring(slug.lastIndexOf('-') + 1);
            return Long.parseLong(idStr);
        } catch (Exception ex) {
            return -1;
        }
    }
}
