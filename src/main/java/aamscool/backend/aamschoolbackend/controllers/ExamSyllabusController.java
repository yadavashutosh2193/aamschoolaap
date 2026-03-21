package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import aamscool.backend.aamschoolbackend.service.ExamSyllabusService;

@RestController
@RequestMapping("/api/syllabus")
public class ExamSyllabusController {

    private final ExamSyllabusService examSyllabusService;

    public ExamSyllabusController(ExamSyllabusService examSyllabusService) {
        this.examSyllabusService = examSyllabusService;
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
