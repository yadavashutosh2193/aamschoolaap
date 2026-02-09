package aamscool.backend.aamschoolbackend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.model.ScrapeRequest;
import aamscool.backend.aamschoolbackend.repository.ScraperConfigRepository;
@Service
public class ScraperConfigService {

    @Autowired
    private ScraperConfigRepository repo;

    /* =============== CREATE =============== */

    public ScrapeRequest create(ScrapeRequest config) {

        if (repo.existsByUrl(config.getUrl())) {
            throw new RuntimeException(
              "Config already exists for this URL");
        }

        return repo.save(config);
    }


    /* =============== READ =============== */

    public List<ScrapeRequest> getAll() {
        return repo.findAll();
    }

    public ScrapeRequest getById(Long id) {

        return repo.findById(id)
            .orElseThrow(() ->
              new RuntimeException("Config not found"));
    }


    /* =============== UPDATE =============== */

    public ScrapeRequest update(Long id, ScrapeRequest newData) {

    	ScrapeRequest old = getById(id);

        // If baseUrl changed, check again
        if (!old.getUrl().equals(newData.getUrl())) {

            if (repo.existsByUrl(newData.getUrl())) {
                throw new RuntimeException("URL already configured");
            }
        }
        old.setUrl(newData.getUrl());
        old.setItemSelector(newData.getItemSelector());
        old.setLinkSelector(newData.getLinkSelector());
        old.setFetchLimit(newData.getFetchLimit());

        return repo.save(old);
    }


    /* =============== DELETE =============== */

    public void delete(Long id) {

        if (!repo.existsById(id)) {
            throw new RuntimeException("Config not found");
        }

        repo.deleteById(id);
    }
}


