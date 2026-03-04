package aamscool.backend.aamschoolbackend.dto;

public class CurrentAffairsQuizSubmissionAnswerDto {

    private Integer questionIndex;
    private String selectedOption;

    public Integer getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }

    public String getSelectedOption() {
        return selectedOption;
    }

    public void setSelectedOption(String selectedOption) {
        this.selectedOption = selectedOption;
    }
}
