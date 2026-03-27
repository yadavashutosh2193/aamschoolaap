package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobMaster;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.repository.JobMasterRepository;
import aamscool.backend.aamschoolbackend.util.LabelUtil;

@Service
public class JobMasterService {
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d[\\d,]*)");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)
    );
    private static final Set<String> LABELS_REQUIRING_POST_COUNT = Set.of(
            "latest-jobs",
            "admit-cards",
            "latest-results",
            "answer-keys"
    );

    private final JobMasterRepository jobMasterRepository;
    private final ObjectMapper objectMapper;
    private final ScrapeCache cache;

    public JobMasterService(JobMasterRepository jobMasterRepository, ObjectMapper objectMapper, ScrapeCache cache) {
        this.jobMasterRepository = jobMasterRepository;
        this.objectMapper = objectMapper;
        this.cache = cache;
    }

    @Transactional
    public JobMaster saveOrUpdate(MasterJobResponseDto dto, String label) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("dto is required");
        }

        String source = safe(dto.getSource());
        Optional<JobMaster> existing =
                (source == null || source.isBlank()) ? Optional.empty() : jobMasterRepository.findBySource(source);
        JobMaster row = existing.orElseGet(JobMaster::new);
        String previousLabel = row.getLabel();
        applyDto(row, dto, label);
        JobMaster saved = jobMasterRepository.save(row);
        refreshCachesAfterWrite(saved, previousLabel);
        return saved;
    }

    @Transactional
    public JobMaster create(MasterJobResponseDto dto, String label) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("dto is required");
        }
        JobMaster row = new JobMaster();
        applyDto(row, dto, label);
        JobMaster saved = jobMasterRepository.save(row);
        refreshCachesAfterWrite(saved, null);
        return saved;
    }

    @Transactional
    public Optional<JobMaster> updateById(long id, MasterJobResponseDto dto, String label) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("dto is required");
        }
        Optional<JobMaster> existing = jobMasterRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        JobMaster row = existing.get();
        String previousLabel = row.getLabel();
        applyDto(row, dto, label);
        JobMaster saved = jobMasterRepository.save(row);
        refreshCachesAfterWrite(saved, previousLabel);
        return Optional.of(saved);
    }

    public List<HomePageLinksModel> getLatestByLabel(String label) {
        List<HomePageLinksModel> out = new ArrayList<>();
        String requestedLabel = safe(label);
        String normalizedRequestedLabel = LabelUtil.normalizeCategoryLabel(requestedLabel);
        boolean enrichTitleWithPostCount = LABELS_REQUIRING_POST_COUNT.contains(normalizedRequestedLabel);
        List<JobMaster> rows = new ArrayList<>();
        for (String candidate : LabelUtil.buildLabelLookupCandidates(requestedLabel)) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            rows = jobMasterRepository.findByLabelIgnoreCaseOrderByUpdatedAtDesc(candidate);
            if (!rows.isEmpty()) {
                break;
            }
        }

        for (JobMaster row : rows) {
            out.add(new HomePageLinksModel(
                    enrichTitleWithPostCount
                            ? buildTitleWithPostCount(safe(row.getTitle()), row.getVacancyDetails())
                            : safe(row.getTitle()),
                    row.getId(),
                    row.getUpdatedAt() != null ? row.getUpdatedAt() : row.getCreatedAt(),
                    parseStringMap(row.getImportantDates())
            ));
        }
        return out;
    }

    public Optional<MasterJobResponseDto> getById(long id) {
        return jobMasterRepository.findById(id).map(this::toDto);
    }

    private MasterJobResponseDto toDto(JobMaster row) {
        MasterJobResponseDto dto = new MasterJobResponseDto();
        dto.setSource(row.getSource());
        dto.setTitle(row.getTitle());
        dto.setShortDescription(row.getShortDescription());
        dto.setAdvertisementNo(row.getAdvertisementNo());
        dto.setPostName(row.getPostName());
        dto.setConductingBody(row.getConductingBody());
        dto.setDatePosted(row.getDatePosted() != null ? row.getDatePosted().toString()
                : (row.getCreatedAt() != null ? row.getCreatedAt().toString() : null));
        dto.setDateUpdated(row.getDateUpdated() != null ? row.getDateUpdated().toString()
                : (row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null));
        dto.setJobLocation(parseObjectMap(row.getJobLocation()));
        dto.setPayScale(row.getPayScale());

        dto.setImportantDates(parseStringMap(row.getImportantDates()));
        dto.setApplicationFee(parseObj(row.getApplicationFee(), MasterJobResponseDto.ApplicationFeeDto.class, new MasterJobResponseDto.ApplicationFeeDto()));
        dto.setEligibilityCriteria(parseObj(row.getEligibilityCriteria(), MasterJobResponseDto.EligibilityCriteriaDto.class, new MasterJobResponseDto.EligibilityCriteriaDto()));
        dto.setVacancyDetails(parseObj(row.getVacancyDetails(), MasterJobResponseDto.VacancyDetailsDto.class, new MasterJobResponseDto.VacancyDetailsDto()));
        dto.setApplicationProcess(parseStringList(row.getApplicationProcess()));
        dto.setExamScheme(parseObjectMap(row.getExamScheme()));
        dto.setSelectionProcess(parseStringList(row.getSelectionProcess()));
        dto.setImportantNotes(parseStringList(row.getImportantNotes()));
        dto.setOfficialLinks(parseObjectMap(row.getOfficialLinks()));
        dto.setSyllabusOverview(parseStringList(row.getSyllabusOverview()));
        dto.setOtherTables(parseTableMap(row.getOtherTables()));
        return dto;
    }

    private String writeJson(Object value) throws Exception {
        if (value == null) return null;
        return objectMapper.writeValueAsString(value);
    }

    private void applyDto(JobMaster row, MasterJobResponseDto dto, String label) throws Exception {
        String source = safe(dto.getSource());
        String safeLabel = safe(label);

        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDate.now());
        }
        row.setUpdatedAt(LocalDate.now());

        if (safeLabel != null && !safeLabel.isBlank()) {
            row.setLabel(LabelUtil.normalizeCategoryLabel(safeLabel));
        }
        if (source != null && !source.isBlank()) {
            row.setSource(source);
        }
        row.setTitle(safe(dto.getTitle()));
        row.setShortDescription(safe(dto.getShortDescription()));
        row.setAdvertisementNo(safe(dto.getAdvertisementNo()));
        row.setPostName(safe(dto.getPostName()));
        row.setConductingBody(safe(dto.getConductingBody()));
        LocalDate parsedPostedDate = parseLocalDate(dto.getDatePosted());
        if (parsedPostedDate != null) {
            row.setDatePosted(parsedPostedDate);
        } else if (row.getDatePosted() == null) {
            row.setDatePosted(row.getCreatedAt());
        }
        LocalDate parsedUpdatedDate = parseLocalDate(dto.getDateUpdated());
        row.setDateUpdated(parsedUpdatedDate != null ? parsedUpdatedDate : row.getUpdatedAt());
        row.setJobLocation(writeJson(dto.getJobLocation()));
        row.setPayScale(safe(dto.getPayScale()));

        row.setImportantDates(writeJson(dto.getImportantDates()));
        row.setApplicationFee(writeJson(dto.getApplicationFee()));
        row.setEligibilityCriteria(writeJson(dto.getEligibilityCriteria()));
        row.setVacancyDetails(writeJson(dto.getVacancyDetails()));
        row.setApplicationProcess(writeJson(dto.getApplicationProcess()));
        row.setExamScheme(writeJson(dto.getExamScheme()));
        row.setSelectionProcess(writeJson(dto.getSelectionProcess()));
        row.setImportantNotes(writeJson(dto.getImportantNotes()));
        row.setOfficialLinks(writeJson(dto.getOfficialLinks()));
        row.setSyllabusOverview(writeJson(dto.getSyllabusOverview()));
        row.setOtherTables(writeJson(dto.getOtherTables()));
    }

    private Map<String, String> parseStringMap(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> parseObjectMap(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> parseStringList(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private Map<String, List<Map<String, String>>> parseTableMap(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, List<Map<String, String>>>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private <T> T parseObj(String json, Class<T> type, T fallback) {
        try {
            if (json == null || json.isBlank()) return fallback;
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }

    private LocalDate parseLocalDate(String value) {
        String text = safe(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (Exception ignored) {
                // try next format
            }
        }
        return null;
    }

    private String buildTitleWithPostCount(String title, String vacancyDetailsJson) {
        if (title == null || title.isBlank()) {
            return title;
        }
        if (containsPostCount(title)) {
            return title;
        }

        String totalVacancy = extractTotalVacancy(vacancyDetailsJson);
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

    private String extractTotalVacancy(String vacancyDetailsJson) {
        if (vacancyDetailsJson == null || vacancyDetailsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(vacancyDetailsJson);
            for (String path : List.of("totalVacancy", "total_vacancy")) {
                String value = textAtPath(root, path);
                String normalized = normalizeVacancyCount(value);
                if (normalized != null) {
                    return normalized;
                }
            }
        } catch (Exception ignored) {
            // Keep latest-jobs API resilient even if one row has malformed vacancy_details JSON.
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

    private void refreshCachesAfterWrite(JobMaster saved, String previousLabel) {
        if (saved == null) {
            return;
        }
        if (previousLabel != null && !previousLabel.isBlank()) {
            cache.invalidateMasterJobsLabel(previousLabel);
        }
        cache.invalidateMasterJobsLabel(saved.getLabel());
        cache.putMasterJob(saved.getId(), toDto(saved));
    }
}
