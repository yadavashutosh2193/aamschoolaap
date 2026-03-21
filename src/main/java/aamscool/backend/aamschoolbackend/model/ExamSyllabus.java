package aamscool.backend.aamschoolbackend.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam_syllabus")
public class ExamSyllabus {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "exam_code", length = 180, unique = true)
    private String examCode;

    @Lob
    @Column(name = "exam_name", columnDefinition = "LONGTEXT")
    private String examName;

    @Lob
    @Column(name = "conducting_body", columnDefinition = "LONGTEXT")
    private String conductingBody;

    @Column(name = "notification_year")
    private Integer notificationYear;

    @Lob
    @Column(name = "payload_json", columnDefinition = "JSON")
    private String payloadJson;

    @Column(name = "created_at")
    private LocalDate createdAt = LocalDate.now();

    @Column(name = "updated_at")
    private LocalDate updatedAt = LocalDate.now();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getConductingBody() {
        return conductingBody;
    }

    public void setConductingBody(String conductingBody) {
        this.conductingBody = conductingBody;
    }

    public Integer getNotificationYear() {
        return notificationYear;
    }

    public void setNotificationYear(Integer notificationYear) {
        this.notificationYear = notificationYear;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
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
}
