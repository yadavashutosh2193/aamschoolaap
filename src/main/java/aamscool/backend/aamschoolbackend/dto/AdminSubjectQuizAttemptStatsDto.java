package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminSubjectQuizAttemptStatsDto {

    private String subject;
    private Integer totalQuizzes;
    private Integer totalAttemptedCandidates;
    private List<AdminQuizAttemptStatDto> quizzes = new ArrayList<>();

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Integer getTotalQuizzes() {
        return totalQuizzes;
    }

    public void setTotalQuizzes(Integer totalQuizzes) {
        this.totalQuizzes = totalQuizzes;
    }

    public Integer getTotalAttemptedCandidates() {
        return totalAttemptedCandidates;
    }

    public void setTotalAttemptedCandidates(Integer totalAttemptedCandidates) {
        this.totalAttemptedCandidates = totalAttemptedCandidates;
    }

    public List<AdminQuizAttemptStatDto> getQuizzes() {
        return quizzes;
    }

    public void setQuizzes(List<AdminQuizAttemptStatDto> quizzes) {
        this.quizzes = quizzes;
    }
}
