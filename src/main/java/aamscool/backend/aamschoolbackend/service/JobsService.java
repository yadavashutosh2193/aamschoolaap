package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.controllers.ScraperScheduler;
import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.repository.JobsRepository;

@Service
public class JobsService {

    @Autowired
    JobsRepository jobDao;

    @Autowired
    TelegramNotifierService telegramNotifierService;

    private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);

    public Optional<JobPosts> getPost(long id) {
        return jobDao.findById(id);
    }

    public JobPosts createJob(JobPosts job) {
        return jobDao.save(job);
    }

    public Optional<JobPosts> updateJob(long id, JobPosts updates) {
        Optional<JobPosts> existing = jobDao.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        JobPosts job = existing.get();
        if (updates.getLabel() != null) {
            job.setLabel(updates.getLabel());
        }
        if (updates.getTitle() != null) {
            job.setTitle(updates.getTitle());
        }
        if (updates.getAdvertisementNo() != null) {
            job.setAdvertisementNo(updates.getAdvertisementNo());
        }
        if (updates.getCreatedAt() != null) {
            job.setCreatedAt(updates.getCreatedAt());
        }
        if (updates.getContent() != null) {
            job.setContent(updates.getContent());
        }
        job.setApproved(updates.isApproved());
        return Optional.of(jobDao.save(job));
    }

    public boolean deleteJob(long id) {
        if (!jobDao.existsById(id)) {
            return false;
        }
        jobDao.deleteById(id);
        return true;
    }

    public List<HomePageLinksModel> getLatestJob(String label) {
        List<HomePageLinksModel> links = jobDao.getJobIdandTile(label);
        links = links.stream()
                .sorted(Comparator.comparing(
                        HomePageLinksModel::getPostDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        return links;
    }

    @Transactional
    public JobPosts savePost(JobPosts job) {
        try {
            Optional<JobPosts> existing = Optional.empty();

            if (job.getLabel() != null && job.getAdvertisementNo() != null
                    && !job.getLabel().isBlank() && !job.getAdvertisementNo().isBlank()) {
                existing = jobDao.findByLabelAndAdvertisementNo(job.getLabel(), job.getAdvertisementNo());
            }

            if (existing.isPresent()) {
                JobPosts old = existing.get();

                old.setTitle(job.getTitle());
                old.setJobId(old.getJobId());
                old.setContent(job.getContent());
                old.setCreatedAt(LocalDate.now());
                old.setApproved(false);
                log.info("saving existing job with job title = {} jobId = {}", old.getTitle(), old.getJobId());
                return jobDao.save(old);

            } else {
                job.setCreatedAt(LocalDate.now());
                job.setApproved(false);
                log.info("saving new job with job title = {}", job.getTitle());

                JobPosts saved = jobDao.save(job);
                try {
                    telegramNotifierService.sendJobUpdate(saved);
                } catch (Exception ex) {
                    log.error("Telegram notification failed for jobId={}", saved.getJobId(), ex);
                }

                return saved;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save job post: " + e.getMessage(), e);
        }
    }

}
