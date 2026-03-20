package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.dto.QuestionBulkUpsertResult;
import aamscool.backend.aamschoolbackend.dto.QuestionDto;
import aamscool.backend.aamschoolbackend.dto.TopicCountDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import aamscool.backend.aamschoolbackend.service.QuestionService;
import aamscool.backend.aamschoolbackend.service.QuizService;

@RestController
@RequestMapping("/questions")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    private final QuestionService questionService;
    private final QuizService quizService;
    private final ObjectMapper objectMapper;

    public QuestionController(QuestionService questionService, QuizService quizService, ObjectMapper objectMapper) {
        this.questionService = questionService;
        this.quizService = quizService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<QuestionDto> getAll() {
        return questionService.getAll();
    }

    @GetMapping("/topics")
    public List<TopicCountDto> getTopics(@RequestParam("subject") String subject) {
        return quizService.getTopicCountsBySubject(subject);
    }

    @GetMapping("/subjects")
    public List<String> getSubjects() {
        return questionService.getAllSubjects();
    }

    @GetMapping("/{id}")
    public QuestionDto getById(@PathVariable Long id) {
        return questionService.getById(id);
    }

    @PostMapping
    public ResponseEntity<QuestionDto> create(@RequestBody QuestionDto dto) {
        QuestionDto created = questionService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/bulk")
    public ResponseEntity<QuestionBulkUpsertResult> bulkUpsert(@RequestBody String payload) {
        List<QuestionDto> questions = extractBulkQuestions(payload);
        log.info("Bulk question upload received: count={}", questions == null ? 0 : questions.size());
        QuestionBulkUpsertResult result = questionService.bulkUpsert(questions);
        log.info("Bulk question upload completed: total={}, created={}, updated={}",
                result.getTotal(), result.getCreated(), result.getUpdated());
        quizService.regenerateAutoQuizzesForBulkUpload(questions);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private List<QuestionDto> extractBulkQuestions(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode questionsNode = root.isArray() ? root : root.get("questions");
            if (questionsNode == null || !questionsNode.isArray()) {
                throw new IllegalArgumentException("questions must be a JSON array");
            }

            // Backward compatibility: allow explanation as plain string in bulk payload.
            for (JsonNode questionNode : questionsNode) {
                if (!questionNode.isObject()) {
                    continue;
                }
                ObjectNode questionObject = (ObjectNode) questionNode;
                JsonNode explanationNode = questionObject.get("explanation");
                if (explanationNode != null && explanationNode.isTextual()
                        && (questionObject.get("explanationText") == null || questionObject.get("explanationText").isNull())) {
                    questionObject.put("explanationText", explanationNode.asText());
                    questionObject.remove("explanation");
                }
            }

            return objectMapper.convertValue(
                    questionsNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, QuestionDto.class)
            );
        } catch (Exception ex) {
            String detail = resolveJsonErrorDetail(ex);
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("Invalid JSON payload", ex);
            }
            throw new IllegalArgumentException("Invalid JSON payload: " + detail, ex);
        }
    }

    private String resolveJsonErrorDetail(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof JsonProcessingException) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return ex.getMessage();
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionDto> update(@PathVariable Long id, @RequestBody QuestionDto dto) {
        QuestionDto updated = questionService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
