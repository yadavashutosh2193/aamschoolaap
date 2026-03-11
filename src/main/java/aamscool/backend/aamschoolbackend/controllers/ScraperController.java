package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.model.ScrapeRequest;
import aamscool.backend.aamschoolbackend.service.GenericScraperService;
import aamscool.backend.aamschoolbackend.service.OpenAIService;

@RestController
@RequestMapping("/api/scrape")
public class ScraperController {

    @Autowired
    private GenericScraperService scraperService;
    
    @Autowired
    private OpenAIService openAiService;
    
    @PostMapping
    public List<Map<String, Object>> scrape(@RequestBody ScrapeRequest request) {

    	List<Map<String, Object>> response = scraperService.scrape(
                request.getUrl(),
                request.getItemSelector(),
                request.getTitleSelector(),
                request.getLinkSelector(),
                request.getFetchLimit()
        );

        return response;
    }
    
    @GetMapping
    public Map<String, Object> scrape(@RequestParam String url) throws Exception {
        return scraperService.scrape(url);
    }
    
    @PutMapping
    public String ProccessJson(@RequestBody String request) throws Exception {

    	String response = openAiService.extractCleanJson(request);

        return response;
    }
    
    
    
}

