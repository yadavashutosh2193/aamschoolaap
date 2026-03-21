package aamscool.backend.aamschoolbackend.dto;

import java.time.LocalDate;

public class ExamSyllabusDetailDto {

    private long id;
    private String slugurl;
    private LocalDate createdAt;
    private LocalDate updatedAt;
    private ExamSyllabusMasterDto syllabus;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSlugurl() {
        return slugurl;
    }

    public void setSlugurl(String slugurl) {
        this.slugurl = slugurl;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ExamSyllabusMasterDto getSyllabus() {
        return syllabus;
    }

    public void setSyllabus(ExamSyllabusMasterDto syllabus) {
        this.syllabus = syllabus;
    }
}
