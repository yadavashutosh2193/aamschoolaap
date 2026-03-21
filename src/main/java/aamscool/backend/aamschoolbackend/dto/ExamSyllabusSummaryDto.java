package aamscool.backend.aamschoolbackend.dto;

import java.time.LocalDate;

public class ExamSyllabusSummaryDto {

    private long id;
    private String examName;
    private String examCode;
    private String slugurl;
    private LocalDate updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getSlugurl() {
        return slugurl;
    }

    public void setSlugurl(String slugurl) {
        this.slugurl = slugurl;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }
}
