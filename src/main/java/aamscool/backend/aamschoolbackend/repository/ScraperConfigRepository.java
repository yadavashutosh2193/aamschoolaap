package aamscool.backend.aamschoolbackend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import aamscool.backend.aamschoolbackend.model.ScrapeRequest;

@Repository
public interface ScraperConfigRepository
        extends JpaRepository<ScrapeRequest, Long> {

	boolean existsByUrl(String baseUrl);

    Optional<ScrapeRequest> findByUrl(String baseUrl);
}

