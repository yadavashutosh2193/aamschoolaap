package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.repository.JobsRepository;
import aamscool.backend.aamschoolbackend.util.LabelUtil;

@Service
public class JobsService {
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d[\\d,]*)");
    private static final Set<String> LABELS_REQUIRING_POST_COUNT = Set.of(
            "latest-jobs",
            "admit-cards",
            "latest-results",
            "answer-keys"
    );

    @Autowired
    JobsRepository jobDao;

    @Autowired
    TelegramNotifierService telegramNotifierService;

    @Autowired
    FacebookPageNotifierService facebookPageNotifierService;

    @Autowired
    ScrapeCache cache;

    private static final Logger log = LoggerFactory.getLogger(JobsService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<JobPosts> getPost(long id) {
        return jobDao.findById(id);
    }

    public JobPosts createJob(JobPosts job) {
        JobPosts saved = jobDao.save(job);
        refreshCachesAfterWrite(saved, null);
        return saved;
    }

    public Optional<JobPosts> updateJob(long id, JobPosts updates) {
        Optional<JobPosts> existing = jobDao.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        JobPosts job = existing.get();
        String previousLabel = job.getLabel();
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
        JobPosts saved = jobDao.save(job);
        refreshCachesAfterWrite(saved, previousLabel);
        return Optional.of(saved);
    }

    public boolean deleteJob(long id) {
        Optional<JobPosts> existing = jobDao.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        jobDao.deleteById(id);
        cache.invalidateJobPost(id);
        cache.invalidateJobsLabel(existing.get().getLabel());
        return true;
    }

    public List<HomePageLinksModel> getLatestJob(String label) {
        boolean enrichTitleWithPostCount = LABELS_REQUIRING_POST_COUNT.contains(
                LabelUtil.normalizeCategoryLabel(label)
        );
        List<HomePageLinksModel> links = jobDao.findByLabelOrderByCreatedAtDesc(label)
                .stream()
                .map(job -> new HomePageLinksModel(
                        enrichTitleWithPostCount ? buildTitleWithPostCount(job.getTitle(), job.getContent()) : job.getTitle(),
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

    private String buildTitleWithPostCount(String title, String content) {
        if (title == null || title.isBlank()) {
            return title;
        }
        if (containsPostCount(title)) {
            return title;
        }

        String totalVacancy = extractTotalVacancy(content);
        if (totalVacancy == null || totalVacancy.isBlank()) {
            return title;
        }
        return title + " (" + totalVacancy + " Posts)";
    }

    private boolean containsPostCount(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String lower = title.toLowerCase();
        return lower.matches(".*\\b\\d[\\d,]*\\s*\\+?\\s*(post|posts|vacancy|vacancies)\\b.*");
    }

    private String extractTotalVacancy(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(content);
            for (String path : List.of(
                    "vacancyDetails.totalVacancy",
                    "vacancyDetails.total_vacancy",
                    "vacancy_details.totalVacancy",
                    "vacancy_details.total_vacancy",
                    "totalVacancy",
                    "total_vacancy"
            )) {
                String value = textAtPath(root, path);
                String normalized = normalizeVacancyCount(value);
                if (normalized != null) {
                    return normalized;
                }
            }
        } catch (Exception ignored) {
            // Keep latest-jobs API resilient even if one row has malformed content JSON.
        }
        return null;
    }

    private String textAtPath(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(part);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private String normalizeVacancyCount(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isBlank()) {
            return null;
        }
        Matcher matcher = DIGIT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1).replace(",", "");
        if (digits.isBlank()) {
            return null;
        }
        return digits;
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
                JobPosts saved = jobDao.save(old);
                refreshCachesAfterWrite(saved, old.getLabel());
                return saved;

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
                try {
                    facebookPageNotifierService.sendJobUpdate(saved);
                } catch (Exception ex) {
                    log.error("Facebook notification failed for jobId={}", saved.getJobId(), ex);
                }

                refreshCachesAfterWrite(saved, null);
                return saved;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save job post: " + e.getMessage(), e);
        }
    }

    private void refreshCachesAfterWrite(JobPosts saved, String previousLabel) {
        if (saved == null) {
            return;
        }
        cache.putJobPost(saved);
        if (previousLabel != null && !previousLabel.isBlank()) {
            cache.invalidateJobsLabel(previousLabel);
        }
        cache.invalidateJobsLabel(saved.getLabel());
    }

}
