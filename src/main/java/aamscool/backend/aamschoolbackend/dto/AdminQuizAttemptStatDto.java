package aamscool.backend.aamschoolbackend.dto;

import java.time.LocalDateTime;

public class AdminQuizAttemptStatDto {

    private Long quizId;
    private String title;
    private String subject;
    private String topic;
    private String subTopic;
    private Integer totalQuestions;
    private Integer totalMarks;
    private Integer attemptedCandidates;
    private LocalDateTime lastAttemptedAt;

    public Long getQuizId() {
        return quizId;
    }

    public void setQuizId(Long quizId) {
        this.quizId = quizId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSubTopic() {
        return subTopic;
    }

    public void setSubTopic(String subTopic) {
        this.subTopic = subTopic;
    }

    public Integer getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public Integer getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(Integer totalMarks) {
        this.totalMarks = totalMarks;
    }

    public Integer getAttemptedCandidates() {
        return attemptedCandidates;
    }

    public void setAttemptedCandidates(Integer attemptedCandidates) {
        this.attemptedCandidates = attemptedCandidates;
    }

    public LocalDateTime getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public void setLastAttemptedAt(LocalDateTime lastAttemptedAt) {
        this.lastAttemptedAt = lastAttemptedAt;
    }
}
