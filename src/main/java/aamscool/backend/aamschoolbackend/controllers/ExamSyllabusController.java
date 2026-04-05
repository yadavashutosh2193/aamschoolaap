package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.dto.ExamSyllabusDetailDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusMasterDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusSummaryDto;
import aamscool.backend.aamschoolbackend.dto.ExamTestSeriesOverviewDto;
import aamscool.backend.aamschoolbackend.dto.AdminQuestionGenerationRequestDto;
import aamscool.backend.aamschoolbackend.dto.AdminSyllabusBatchGenerationRequestDto;
import aamscool.backend.aamschoolbackend.service.AdminSyllabusQuestionService;
import aamscool.backend.aamschoolbackend.service.ExamSyllabusService;
import aamscool.backend.aamschoolbackend.service.ExamTestSeriesService;
import aamscool.backend.aamschoolbackend.service.McqGenerationArtifactService;

@RestController
@RequestMapping("/api/syllabus")
public class ExamSyllabusController {

    private final ExamSyllabusService examSyllabusService;
    private final ExamTestSeriesService examTestSeriesService;
    private final AdminSyllabusQuestionService adminSyllabusQuestionService;
    private final McqGenerationArtifactService mcqGenerationArtifactService;

    public ExamSyllabusController(ExamSyllabusService examSyllabusService,
                                  ExamTestSeriesService examTestSeriesService,
                                  AdminSyllabusQuestionService adminSyllabusQuestionService,
                                  McqGenerationArtifactService mcqGenerationArtifactService) {
        this.examSyllabusService = examSyllabusService;
        this.examTestSeriesService = examTestSeriesService;
        this.adminSyllabusQuestionService = adminSyllabusQuestionService;
        this.mcqGenerationArtifactService = mcqGenerationArtifactService;
    }

    @GetMapping("/latest")
    public List<ExamSyllabusSummaryDto> getLatestSyllabus() {
        return examSyllabusService.getAllSummaries();
    }

    @GetMapping("/{slugWithId}")
    public ResponseEntity<?> getBySlugWithId(@PathVariable("slugWithId") String slugWithId) {
        return examSyllabusService.getBySlugWithId(slugWithId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found"
                )));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") long id) {
        return examSyllabusService.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found"
                )));
    }

    @GetMapping("/exam/{examKey}")
    public ResponseEntity<?> getByExamKey(@PathVariable("examKey") String examKey) {
        return examSyllabusService.getByExamKey(examKey)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @GetMapping("/exam/{examKey}/test-series-blueprint")
    public ResponseEntity<?> getTestSeriesBlueprint(@PathVariable("examKey") String examKey) {
        return examSyllabusService.getTestSeriesBlueprintByExamKey(examKey)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @GetMapping("/admin")
    public List<ExamSyllabusSummaryDto> adminList() {
        return examSyllabusService.getAllSummaries();
    }

    @GetMapping("/admin/{id}")
    public ResponseEntity<?> adminGetById(@PathVariable("id") long id) {
        return examSyllabusService.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found"
                )));
    }

    @GetMapping("/admin/test-series/overview")
    public List<ExamTestSeriesOverviewDto> adminTestSeriesOverview() {
        return examTestSeriesService.getOverviewForAllExams();
    }

    @GetMapping("/admin/test-series/overview/{examKey}")
    public ResponseEntity<?> adminTestSeriesOverviewByExam(@PathVariable("examKey") String examKey) {
        return examTestSeriesService.getOverviewByExamKey(examKey)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @GetMapping("/admin/question-bank/coverage/{examKey}")
    public ResponseEntity<?> adminQuestionBankCoverage(@PathVariable("examKey") String examKey) {
        return adminSyllabusQuestionService.getCoverageByExamKey(examKey)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @PostMapping("/admin/question-bank/generate-preview/{examKey}")
    public ResponseEntity<?> adminGenerateQuestionPreview(@PathVariable("examKey") String examKey,
                                                          @RequestBody AdminQuestionGenerationRequestDto request) {
        return adminSyllabusQuestionService.generatePreviewByExamKey(examKey, request)
                .<ResponseEntity<?>>map(preview -> ResponseEntity.ok(preview.getQuestions()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @PostMapping("/admin/question-bank/generate-and-save/{examKey}")
    public ResponseEntity<?> adminGenerateAndSaveQuestionBank(@PathVariable("examKey") String examKey,
                                                              @RequestBody(required = false) AdminSyllabusBatchGenerationRequestDto request) {
        return adminSyllabusQuestionService.generateAndSaveAllByExamKey(examKey, request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @GetMapping(value = "/admin/question-bank/artifacts/{artifactId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> adminDownloadGeneratedQuestionArtifact(@PathVariable("artifactId") String artifactId) {
        byte[] content = mcqGenerationArtifactService.readArtifact(artifactId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifactId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }

    @PostMapping("/admin/test-series/generate/{examKey}")
    public ResponseEntity<?> adminGenerateTestSeriesByExam(@PathVariable("examKey") String examKey) {
        return examTestSeriesService.generateAllPossibleByExamKey(examKey)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found for exam key"
                )));
    }

    @PostMapping("/admin")
    public ResponseEntity<ExamSyllabusDetailDto> adminCreate(@RequestBody ExamSyllabusMasterDto payload) {
        ExamSyllabusDetailDto saved = examSyllabusService.create(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/admin/{id}")
    public ResponseEntity<?> adminUpdate(@PathVariable("id") long id,
                                         @RequestBody ExamSyllabusMasterDto payload) {
        return examSyllabusService.updateById(id, payload)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "error",
                        "message", "Syllabus not found"
                )));
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> adminDelete(@PathVariable("id") long id) {
        boolean deleted = examSyllabusService.deleteById(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "error",
                    "message", "Syllabus not found"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Syllabus deleted"
        ));
    }
}
