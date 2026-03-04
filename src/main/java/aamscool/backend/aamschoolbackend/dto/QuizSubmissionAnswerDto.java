package aamscool.backend.aamschoolbackend.dto;

public class QuizSubmissionAnswerDto {

    private Long quizQuestionId;
    private Long selectedChoiceId;

    public Long getQuizQuestionId() {
        return quizQuestionId;
    }

    public void setQuizQuestionId(Long quizQuestionId) {
        this.quizQuestionId = quizQuestionId;
    }

    public Long getSelectedChoiceId() {
        return selectedChoiceId;
    }

    public void setSelectedChoiceId(Long selectedChoiceId) {
        this.selectedChoiceId = selectedChoiceId;
    }
}
