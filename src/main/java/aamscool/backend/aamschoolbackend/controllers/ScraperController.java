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
import aamscool.backend.aamschoolbackend.util.SarkariResultDsssbNormalizer;

@RestController
@RequestMapping("/api/scrape")
public class ScraperController {

    @Autowired
    private GenericScraperService scraperService;
    
    @Autowired
    private SarkariResultDsssbNormalizer sarkariResultDsssbNormalizer;
    
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

    	Map<String, Object> resp = scraperService.scrape(url);
    	//Map<String, Object> resp1 = jobJsonNormalizerService.normalize(resp);
    	String aiJson = openAiService.processJobMap(resp);
    	System.out.println("++++++++++++++++++++++++++++++++++++++++++++++");
    	System.out.println(openAiService.extractCleanJson(aiJson));
    	System.out.println("++++++++++++++++++++++++++++++++++++++++++++++");
    	Map<String, Object> resp2 = sarkariResultDsssbNormalizer.normalize(resp);
        return resp2;
    }
    
    @PutMapping
    public String ProccessJson(@RequestBody String request) throws Exception {

    	String response = openAiService.extractCleanJson(request);

        return response;
    }
    
    
    
}

