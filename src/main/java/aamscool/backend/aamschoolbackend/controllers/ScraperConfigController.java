package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.model.ScrapeRequest;
import aamscool.backend.aamschoolbackend.service.ScraperConfigService;
import aamscool.backend.aamschoolbackend.service.ScraperService;
@RestController
@RequestMapping("/api/scraperconfig")
@CrossOrigin("*")
public class ScraperConfigController {

    @Autowired
    private ScraperConfigService service;

    @Autowired
    private ScraperService scraperService;

    /* =============== CREATE =============== */

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody ScrapeRequest config) {

        try {

            return ResponseEntity
              .status(HttpStatus.CREATED)
              .body(service.create(config));

        } catch (Exception e) {

            return ResponseEntity
              .status(HttpStatus.CONFLICT)
              .body(e.getMessage());
        }
    }


    /* =============== READ =============== */

    @GetMapping
    public List<ScrapeRequest> getAll() {
        return service.getAll();
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {

        try {
            return ResponseEntity.ok(service.getById(id));
        } catch (Exception e) {

            return ResponseEntity
              .status(HttpStatus.NOT_FOUND)
              .body(e.getMessage());
        }
    }


    /* =============== UPDATE =============== */

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody ScrapeRequest config) {

        try {

            return ResponseEntity.ok(
                service.update(id, config));

        } catch (Exception e) {

            return ResponseEntity
              .status(HttpStatus.BAD_REQUEST)
              .body(e.getMessage());
        }
    }


    /* =============== DELETE =============== */

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {

        try {

            service.delete(id);

            return ResponseEntity.ok("Deleted");

        } catch (Exception e) {

            return ResponseEntity
              .status(HttpStatus.NOT_FOUND)
              .body(e.getMessage());
        }
    }
    
    @PostMapping("/runnow")
    public ResponseEntity<String> runNow() {

        scraperService.runScraperAsync();

        return ResponseEntity.ok("Scraper started");
    }

}


