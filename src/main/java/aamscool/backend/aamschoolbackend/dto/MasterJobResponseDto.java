package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MasterJobResponseDto {

    private String title;
    private String shortDescription;
    private String advertisementNo;
    private String postName;
    private String conductingBody;
    private Map<String, String> importantDates;
    private ApplicationFeeDto applicationFee;
    private EligibilityCriteriaDto eligibilityCriteria;
    private VacancyDetailsDto vacancyDetails;
    private String payScale;
    private List<String> applicationProcess;
    private Map<String, Object> examScheme;
    private List<String> selectionProcess;
    private List<String> importantNotes;
    private Map<String, Object> officialLinks;
    private Map<String, List<Map<String, String>>> otherTables;
    private String source;
    private List<String> syllabusOverview;

    public MasterJobResponseDto() {
        this.title = null;
        this.shortDescription = null;
        this.advertisementNo = null;
        this.postName = null;
        this.conductingBody = null;
        this.importantDates = new LinkedHashMap<>();
        this.applicationFee = new ApplicationFeeDto();
        this.eligibilityCriteria = new EligibilityCriteriaDto();
        this.vacancyDetails = new VacancyDetailsDto();
        this.payScale = null;
        this.applicationProcess = new ArrayList<>();
        this.examScheme = new LinkedHashMap<>();
        this.selectionProcess = new ArrayList<>();
        this.importantNotes = new ArrayList<>();
        this.officialLinks = new LinkedHashMap<>();
        this.otherTables = new LinkedHashMap<>();
        this.source = null;
        this.syllabusOverview = new ArrayList<>();
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

    public Map<String, String> getImportantDates() {
        return importantDates;
    }

    public void setImportantDates(Map<String, String> importantDates) {
        this.importantDates = importantDates;
    }

    public ApplicationFeeDto getApplicationFee() {
        return applicationFee;
    }

    public void setApplicationFee(ApplicationFeeDto applicationFee) {
        this.applicationFee = applicationFee;
    }

    public EligibilityCriteriaDto getEligibilityCriteria() {
        return eligibilityCriteria;
    }

    public void setEligibilityCriteria(EligibilityCriteriaDto eligibilityCriteria) {
        this.eligibilityCriteria = eligibilityCriteria;
    }

    public VacancyDetailsDto getVacancyDetails() {
        return vacancyDetails;
    }

    public void setVacancyDetails(VacancyDetailsDto vacancyDetails) {
        this.vacancyDetails = vacancyDetails;
    }

    public String getPayScale() {
        return payScale;
    }

    public void setPayScale(String payScale) {
        this.payScale = payScale;
    }

    public List<String> getApplicationProcess() {
        return applicationProcess;
    }

    public void setApplicationProcess(List<String> applicationProcess) {
        this.applicationProcess = applicationProcess;
    }

    public Map<String, Object> getExamScheme() {
        return examScheme;
    }

    public void setExamScheme(Map<String, Object> examScheme) {
        this.examScheme = examScheme;
    }

    public List<String> getSelectionProcess() {
        return selectionProcess;
    }

    public void setSelectionProcess(List<String> selectionProcess) {
        this.selectionProcess = selectionProcess;
    }

    public List<String> getImportantNotes() {
        return importantNotes;
    }

    public void setImportantNotes(List<String> importantNotes) {
        this.importantNotes = importantNotes;
    }

    public Map<String, Object> getOfficialLinks() {
        return officialLinks;
    }

    public void setOfficialLinks(Map<String, Object> officialLinks) {
        this.officialLinks = officialLinks;
    }

    public Map<String, List<Map<String, String>>> getOtherTables() {
        return otherTables;
    }

    public void setOtherTables(Map<String, List<Map<String, String>>> otherTables) {
        this.otherTables = otherTables;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getSyllabusOverview() {
        return syllabusOverview;
    }

    public void setSyllabusOverview(List<String> syllabusOverview) {
        this.syllabusOverview = syllabusOverview;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicationFeeDto {
        private String generalObc;
        private String scStEbcFemaleTransgender;
        private String refundGeneralObcAfterCbt;
        private String refundScStEbcFemaleTransgenderAfterCbt;
        private List<String> paymentMode = new ArrayList<>();
        private List<Map<String, String>> slabRows = new ArrayList<>();

        public String getGeneralObc() {
            return generalObc;
        }

        public void setGeneralObc(String generalObc) {
            this.generalObc = generalObc;
        }

        public String getScStEbcFemaleTransgender() {
            return scStEbcFemaleTransgender;
        }

        public void setScStEbcFemaleTransgender(String scStEbcFemaleTransgender) {
            this.scStEbcFemaleTransgender = scStEbcFemaleTransgender;
        }

        public String getRefundGeneralObcAfterCbt() {
            return refundGeneralObcAfterCbt;
        }

        public void setRefundGeneralObcAfterCbt(String refundGeneralObcAfterCbt) {
            this.refundGeneralObcAfterCbt = refundGeneralObcAfterCbt;
        }

        public String getRefundScStEbcFemaleTransgenderAfterCbt() {
            return refundScStEbcFemaleTransgenderAfterCbt;
        }

        public void setRefundScStEbcFemaleTransgenderAfterCbt(String refundScStEbcFemaleTransgenderAfterCbt) {
            this.refundScStEbcFemaleTransgenderAfterCbt = refundScStEbcFemaleTransgenderAfterCbt;
        }

        public List<String> getPaymentMode() {
            return paymentMode;
        }

        public void setPaymentMode(List<String> paymentMode) {
            this.paymentMode = paymentMode;
        }

        public List<Map<String, String>> getSlabRows() {
            return slabRows;
        }

        public void setSlabRows(List<Map<String, String>> slabRows) {
            this.slabRows = slabRows;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class EligibilityCriteriaDto {
        private String minimumAge;
        private String maximumAge;
        private String ageAsOn;
        private String qualification;
        private Map<String, String> postWiseQualification = new LinkedHashMap<>();

        public String getMinimumAge() {
            return minimumAge;
        }

        public void setMinimumAge(String minimumAge) {
            this.minimumAge = minimumAge;
        }

        public String getMaximumAge() {
            return maximumAge;
        }

        public void setMaximumAge(String maximumAge) {
            this.maximumAge = maximumAge;
        }

        public String getAgeAsOn() {
            return ageAsOn;
        }

        public void setAgeAsOn(String ageAsOn) {
            this.ageAsOn = ageAsOn;
        }

        public String getQualification() {
            return qualification;
        }

        public void setQualification(String qualification) {
            this.qualification = qualification;
        }

        public Map<String, String> getPostWiseQualification() {
            return postWiseQualification;
        }

        public void setPostWiseQualification(Map<String, String> postWiseQualification) {
            this.postWiseQualification = postWiseQualification;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VacancyDetailsDto {
        private String totalVacancy;
        private Map<String, String> postWise = new LinkedHashMap<>();
        private Map<String, String> categoryWise = new LinkedHashMap<>();
        private List<Map<String, String>> tableRows = new ArrayList<>();

        public String getTotalVacancy() {
            return totalVacancy;
        }

        public void setTotalVacancy(String totalVacancy) {
            this.totalVacancy = totalVacancy;
        }

        public Map<String, String> getPostWise() {
            return postWise;
        }

        public void setPostWise(Map<String, String> postWise) {
            this.postWise = postWise;
        }

        public Map<String, String> getCategoryWise() {
            return categoryWise;
        }

        public void setCategoryWise(Map<String, String> categoryWise) {
            this.categoryWise = categoryWise;
        }

        public List<Map<String, String>> getTableRows() {
            return tableRows;
        }

        public void setTableRows(List<Map<String, String>> tableRows) {
            this.tableRows = tableRows;
        }
    }
}
