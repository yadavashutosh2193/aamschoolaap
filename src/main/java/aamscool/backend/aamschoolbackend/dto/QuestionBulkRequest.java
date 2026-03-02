package aamscool.backend.aamschoolbackend.dto;

import java.util.List;

public class QuestionBulkRequest {
    private List<QuestionDto> questions;

    public List<QuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDto> questions) {
        this.questions = questions;
    }
}
