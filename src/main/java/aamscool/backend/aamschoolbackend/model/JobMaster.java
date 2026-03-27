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
@Table(name = "job_master")
public class JobMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(length = 120)
    private String label;

    @Column(length = 2048, unique = true)
    private String source;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String title;

    @Lob
    @Column(name = "short_description", columnDefinition = "LONGTEXT")
    private String shortDescription;

    @Column(name = "advertisement_no", length = 120)
    private String advertisementNo;

    @Lob
    @Column(name = "post_name", columnDefinition = "LONGTEXT")
    private String postName;

    @Lob
    @Column(name = "conducting_body", columnDefinition = "LONGTEXT")
    private String conductingBody;

    @Column(name = "date_posted")
    private LocalDate datePosted;

    @Column(name = "date_updated")
    private LocalDate dateUpdated;

    @Lob
    @Column(name = "job_location", columnDefinition = "JSON")
    private String jobLocation;

    @Lob
    @Column(name = "pay_scale", columnDefinition = "LONGTEXT")
    private String payScale;

    @Lob
    @Column(name = "important_dates", columnDefinition = "JSON")
    private String importantDates;

    @Lob
    @Column(name = "application_fee", columnDefinition = "JSON")
    private String applicationFee;

    @Lob
    @Column(name = "eligibility_criteria", columnDefinition = "JSON")
    private String eligibilityCriteria;

    @Lob
    @Column(name = "vacancy_details", columnDefinition = "JSON")
    private String vacancyDetails;

    @Lob
    @Column(name = "application_process", columnDefinition = "JSON")
    private String applicationProcess;

    @Lob
    @Column(name = "exam_scheme", columnDefinition = "JSON")
    private String examScheme;

    @Lob
    @Column(name = "selection_process", columnDefinition = "JSON")
    private String selectionProcess;

    @Lob
    @Column(name = "important_notes", columnDefinition = "JSON")
    private String importantNotes;

    @Lob
    @Column(name = "official_links", columnDefinition = "JSON")
    private String officialLinks;

    @Lob
    @Column(name = "syllabus_overview", columnDefinition = "JSON")
    private String syllabusOverview;

    @Lob
    @Column(name = "other_tables", columnDefinition = "JSON")
    private String otherTables;

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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getAdvertisementNo() {
        return advertisementNo;
    }

    public void setAdvertisementNo(String advertisementNo) {
        this.advertisementNo = advertisementNo;
    }

    public String getPostName() {
        return postName;
    }

    public void setPostName(String postName) {
        this.postName = postName;
    }

    public String getConductingBody() {
        return conductingBody;
    }

    public void setConductingBody(String conductingBody) {
        this.conductingBody = conductingBody;
    }

    public LocalDate getDatePosted() {
        return datePosted;
    }

    public void setDatePosted(LocalDate datePosted) {
        this.datePosted = datePosted;
    }

    public LocalDate getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(LocalDate dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public String getJobLocation() {
        return jobLocation;
    }

    public void setJobLocation(String jobLocation) {
        this.jobLocation = jobLocation;
    }

    public String getPayScale() {
        return payScale;
    }

    public void setPayScale(String payScale) {
        this.payScale = payScale;
    }

    public String getImportantDates() {
        return importantDates;
    }

    public void setImportantDates(String importantDates) {
        this.importantDates = importantDates;
    }

    public String getApplicationFee() {
        return applicationFee;
    }

    public void setApplicationFee(String applicationFee) {
        this.applicationFee = applicationFee;
    }

    public String getEligibilityCriteria() {
        return eligibilityCriteria;
    }

    public void setEligibilityCriteria(String eligibilityCriteria) {
        this.eligibilityCriteria = eligibilityCriteria;
    }

    public String getVacancyDetails() {
        return vacancyDetails;
    }

    public void setVacancyDetails(String vacancyDetails) {
        this.vacancyDetails = vacancyDetails;
    }

    public String getApplicationProcess() {
        return applicationProcess;
    }

    public void setApplicationProcess(String applicationProcess) {
        this.applicationProcess = applicationProcess;
    }

    public String getExamScheme() {
        return examScheme;
    }

    public void setExamScheme(String examScheme) {
        this.examScheme = examScheme;
    }

    public String getSelectionProcess() {
        return selectionProcess;
    }

    public void setSelectionProcess(String selectionProcess) {
        this.selectionProcess = selectionProcess;
    }

    public String getImportantNotes() {
        return importantNotes;
    }

    public void setImportantNotes(String importantNotes) {
        this.importantNotes = importantNotes;
    }

    public String getOfficialLinks() {
        return officialLinks;
    }

    public void setOfficialLinks(String officialLinks) {
        this.officialLinks = officialLinks;
    }

    public String getSyllabusOverview() {
        return syllabusOverview;
    }

    public void setSyllabusOverview(String syllabusOverview) {
        this.syllabusOverview = syllabusOverview;
    }

    public String getOtherTables() {
        return otherTables;
    }

    public void setOtherTables(String otherTables) {
        this.otherTables = otherTables;
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
