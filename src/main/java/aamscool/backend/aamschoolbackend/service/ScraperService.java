package aamscool.backend.aamschoolbackend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.config.ScraperLock;
import aamscool.backend.aamschoolbackend.model.ScrapeRequest;

@Service
public class ScraperService {

	@Autowired
	private ScraperLock lock;

	@Autowired
    private ScraperConfigService service;
	@Autowired
    private GenericScraperService scraperService;

	
	@Async
	public void runScraperAsync() {
	    runScraper();
	}
	
	public void runScraper() {

	    if (!lock.tryLock()) {
	        //log.warn("Scraper already running");
	        return;
	    }

		try {

			List<ScrapeRequest> scrapeRequest = service.getAll();
			scrapeRequest.forEach(request -> scraperService.scrape(request.getUrl(), request.getItemSelector(),
					request.getTitleSelector(), request.getLinkSelector(), request.getFetchLimit()));

		} finally {

	        lock.unlock();
	    }
	}

}

