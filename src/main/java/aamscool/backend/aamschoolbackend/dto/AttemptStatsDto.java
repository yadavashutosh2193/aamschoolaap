package aamscool.backend.aamschoolbackend.dto;

public class AttemptStatsDto {

    private Long totalAttempts;
    private Long correctAttempts;
    private Double accuracy;

    public Long getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(Long totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public Long getCorrectAttempts() {
        return correctAttempts;
    }

    public void setCorrectAttempts(Long correctAttempts) {
        this.correctAttempts = correctAttempts;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }
}