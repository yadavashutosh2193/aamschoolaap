package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.repository.JobsRepository;

@Service
public class JobsService {

    @Autowired
    JobsRepository jobDao;

    @Autowired
    TelegramNotifierService telegramNotifierService;

    private static final Logger log = LoggerFactory.getLogger(JobsService.class);
    private final ObjectMapper mapper = new ObjectMapper();

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
        List<HomePageLinksModel> links = jobDao.findByLabelOrderByCreatedAtDesc(label)
                .stream()
                .map(job -> new HomePageLinksModel(
                        job.getTitle(),
                        job.getJobId(),
                        job.getCreatedAt(),
                        extractImportantDates(job.getContent())
                ))
                .sorted(Comparator.comparing(
                        HomePageLinksModel::getPostDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        return links;
    }

    private Map<String, String> extractImportantDates(String content) {
        Map<String, String> out = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return out;
        }

        try {
            JsonNode root = mapper.readTree(content);
            JsonNode dates = root.path("importantDates");
            if (dates.isMissingNode() || dates.isNull() || !dates.isObject()) {
                dates = root.path("important_dates");
            }
            if (dates.isObject()) {
                dates.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value == null || value.isNull()) {
                        return;
                    }
                    String text = value.isValueNode() ? value.asText() : value.toString();
                    if (text != null && !text.isBlank()) {
                        out.put(entry.getKey(), text);
                    }
                });
            }
        } catch (Exception ignored) {
            // Keep latest-jobs API resilient even if one row has malformed content JSON.
        }

        return out;
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
