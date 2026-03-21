package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.dto.ExamSyllabusDetailDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusMasterDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusSummaryDto;
import aamscool.backend.aamschoolbackend.model.ExamSyllabus;
import aamscool.backend.aamschoolbackend.repository.ExamSyllabusRepository;

@Service
public class ExamSyllabusService {

    private final ExamSyllabusRepository examSyllabusRepository;
    private final ObjectMapper objectMapper;
    private final TelegramNotifierService telegramNotifierService;

    public ExamSyllabusService(ExamSyllabusRepository examSyllabusRepository,
                               ObjectMapper objectMapper,
                               TelegramNotifierService telegramNotifierService) {
        this.examSyllabusRepository = examSyllabusRepository;
        this.objectMapper = objectMapper;
        this.telegramNotifierService = telegramNotifierService;
    }

    public List<ExamSyllabusSummaryDto> getAllSummaries() {
        List<ExamSyllabusSummaryDto> out = new ArrayList<>();
        for (ExamSyllabus row : examSyllabusRepository.findAllByOrderByUpdatedAtDesc()) {
            out.add(toSummary(row));
        }
        return out;
    }

    public Optional<ExamSyllabusDetailDto> getBySlugWithId(String slugWithId) {
        long id = extractIdFromSlug(slugWithId);
        if (id <= 0) {
            return Optional.empty();
        }
        return getById(id);
    }

    public Optional<ExamSyllabusDetailDto> getById(long id) {
        return examSyllabusRepository.findById(id).map(this::toDetail);
    }

    @Transactional
    public ExamSyllabusDetailDto create(ExamSyllabusMasterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Syllabus payload is required");
        }
        ExamSyllabus row = new ExamSyllabus();
        applyPayload(row, dto, true);
        ExamSyllabus saved = examSyllabusRepository.save(row);
        try {
            telegramNotifierService.sendExamSyllabusUpdate(saved, false);
        } catch (Exception ignored) {
            // Notification failure should not break write flow.
        }
        return toDetail(saved);
    }

    @Transactional
    public Optional<ExamSyllabusDetailDto> updateById(long id, ExamSyllabusMasterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Syllabus payload is required");
        }
        Optional<ExamSyllabus> existing = examSyllabusRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        ExamSyllabus row = existing.get();
        applyPayload(row, dto, false);
        ExamSyllabus saved = examSyllabusRepository.save(row);
        try {
            telegramNotifierService.sendExamSyllabusUpdate(saved, true);
        } catch (Exception ignored) {
            // Notification failure should not break write flow.
        }
        return Optional.of(toDetail(saved));
    }

    @Transactional
    public boolean deleteById(long id) {
        if (!examSyllabusRepository.existsById(id)) {
            return false;
        }
        examSyllabusRepository.deleteById(id);
        return true;
    }

    private void applyPayload(ExamSyllabus row, ExamSyllabusMasterDto dto, boolean isCreate) {
        String normalizedCode = normalizeExamCode(dto.getExamCode(), dto.getExamName());
        Optional<ExamSyllabus> byCode = examSyllabusRepository.findByExamCodeIgnoreCase(normalizedCode);
        if (isCreate && byCode.isPresent()) {
            throw new IllegalArgumentException("Syllabus exam code already exists");
        }
        if (!isCreate && byCode.isPresent() && byCode.get().getId() != row.getId()) {
            throw new IllegalArgumentException("Syllabus exam code already exists");
        }

        dto.setExamCode(normalizedCode);
        row.setExamCode(normalizedCode);
        row.setExamName(safe(dto.getExamName()));
        row.setConductingBody(safe(dto.getConductingBody()));
        row.setNotificationYear(dto.getNotificationYear());

        try {
            row.setPayloadJson(objectMapper.writeValueAsString(dto));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid syllabus JSON payload");
        }

        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDate.now());
        }
        row.setUpdatedAt(LocalDate.now());
    }

    private ExamSyllabusSummaryDto toSummary(ExamSyllabus row) {
        ExamSyllabusSummaryDto dto = new ExamSyllabusSummaryDto();
        dto.setId(row.getId());
        dto.setExamName(row.getExamName());
        dto.setExamCode(row.getExamCode());
        dto.setUpdatedAt(row.getUpdatedAt());
        dto.setSlugurl(buildSyllabusSlug(row.getExamName(), row.getId()));
        return dto;
    }

    private ExamSyllabusDetailDto toDetail(ExamSyllabus row) {
        ExamSyllabusDetailDto dto = new ExamSyllabusDetailDto();
        dto.setId(row.getId());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        dto.setSlugurl(buildSyllabusSlug(row.getExamName(), row.getId()));
        dto.setSyllabus(readSyllabusPayload(row));
        return dto;
    }

    private ExamSyllabusMasterDto readSyllabusPayload(ExamSyllabus row) {
        if (row.getPayloadJson() == null || row.getPayloadJson().isBlank()) {
            return fallbackPayload(row);
        }
        try {
            ExamSyllabusMasterDto parsed = objectMapper.readValue(row.getPayloadJson(), ExamSyllabusMasterDto.class);
            if (parsed.getExamCode() == null || parsed.getExamCode().isBlank()) {
                parsed.setExamCode(row.getExamCode());
            }
            if (parsed.getExamName() == null || parsed.getExamName().isBlank()) {
                parsed.setExamName(row.getExamName());
            }
            if (parsed.getConductingBody() == null || parsed.getConductingBody().isBlank()) {
                parsed.setConductingBody(row.getConductingBody());
            }
            if (parsed.getNotificationYear() == null) {
                parsed.setNotificationYear(row.getNotificationYear());
            }
            return parsed;
        } catch (Exception ex) {
            return fallbackPayload(row);
        }
    }

    private ExamSyllabusMasterDto fallbackPayload(ExamSyllabus row) {
        ExamSyllabusMasterDto dto = new ExamSyllabusMasterDto();
        dto.setExamCode(row.getExamCode());
        dto.setExamName(row.getExamName());
        dto.setConductingBody(row.getConductingBody());
        dto.setNotificationYear(row.getNotificationYear());
        return dto;
    }

    private long extractIdFromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return -1;
        }
        try {
            String idText = slug.substring(slug.lastIndexOf('-') + 1);
            return Long.parseLong(idText);
        } catch (Exception ex) {
            return -1;
        }
    }

    private String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "syllabus";
        }
        String slug = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        return slug.isBlank() ? "syllabus" : slug;
    }

    private String buildSyllabusSlug(String examName, long id) {
        return toSlug(examName) + "-syllabus-" + id;
    }

    private String normalizeExamCode(String examCode, String examName) {
        String code = safe(examCode);
        if (code == null || code.isBlank()) {
            code = safe(examName);
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("examCode or examName is required");
        }
        code = code.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (code.isBlank()) {
            throw new IllegalArgumentException("examCode is invalid");
        }
        return code;
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }
}
