package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobMaster;
import aamscool.backend.aamschoolbackend.repository.JobMasterRepository;
import aamscool.backend.aamschoolbackend.util.LabelUtil;

@Service
public class JobMasterService {

    private final JobMasterRepository jobMasterRepository;
    private final ObjectMapper objectMapper;

    public JobMasterService(JobMasterRepository jobMasterRepository, ObjectMapper objectMapper) {
        this.jobMasterRepository = jobMasterRepository;
        this.objectMapper = objectMapper;
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
        applyDto(row, dto, label);
        return jobMasterRepository.save(row);
    }

    @Transactional
    public JobMaster create(MasterJobResponseDto dto, String label) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("dto is required");
        }
        JobMaster row = new JobMaster();
        applyDto(row, dto, label);
        return jobMasterRepository.save(row);
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
        applyDto(row, dto, label);
        return Optional.of(jobMasterRepository.save(row));
    }

    public List<HomePageLinksModel> getLatestByLabel(String label) {
        List<HomePageLinksModel> out = new ArrayList<>();
        String requestedLabel = safe(label);
        List<JobMaster> rows = new ArrayList<>();
        for (String candidate : LabelUtil.buildLabelLookupCandidates(requestedLabel)) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            rows = jobMasterRepository.findByLabelIgnoreCaseOrderByCreatedAtDesc(candidate);
            if (!rows.isEmpty()) {
                break;
            }
        }

        for (JobMaster row : rows) {
            out.add(new HomePageLinksModel(
                    safe(row.getTitle()),
                    row.getId(),
                    row.getCreatedAt(),
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
}
