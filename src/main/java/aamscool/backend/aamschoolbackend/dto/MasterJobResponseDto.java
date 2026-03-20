package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MasterJobResponseDto {

    private String title;
    @JsonAlias("shortDescription")
    private String shortDescription;
    @JsonAlias("advertisementNo")
    private String advertisementNo;
    @JsonAlias("postName")
    private String postName;
    @JsonAlias("conductingBody")
    private String conductingBody;
    @JsonAlias("importantDates")
    private Map<String, String> importantDates;
    @JsonAlias("applicationFee")
    private ApplicationFeeDto applicationFee;
    @JsonAlias("eligibilityCriteria")
    private EligibilityCriteriaDto eligibilityCriteria;
    @JsonAlias("vacancyDetails")
    private VacancyDetailsDto vacancyDetails;
    @JsonAlias("payScale")
    private String payScale;
    @JsonAlias("applicationProcess")
    private List<String> applicationProcess;
    @JsonAlias("examScheme")
    private Map<String, Object> examScheme;
    @JsonAlias("selectionProcess")
    private List<String> selectionProcess;
    @JsonAlias("importantNotes")
    private List<String> importantNotes;
    @JsonAlias("officialLinks")
    private Map<String, Object> officialLinks;
    @JsonAlias("otherTables")
    private Map<String, List<Map<String, String>>> otherTables;
    @JsonIgnore
    private String source;
    @JsonAlias("syllabusOverview")
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

    @JsonIgnore
    public String getSource() {
        return source;
    }

    @JsonIgnore
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationFeeDto {
        @JsonAlias("generalObc")
        private String generalObc;
        @JsonAlias("scStEbcFemaleTransgender")
        private String scStEbcFemaleTransgender;
        @JsonAlias("refundGeneralObcAfterCbt")
        private String refundGeneralObcAfterCbt;
        @JsonAlias("refundScStEbcFemaleTransgenderAfterCbt")
        private String refundScStEbcFemaleTransgenderAfterCbt;
        @JsonAlias("paymentMode")
        private List<String> paymentMode = new ArrayList<>();
        @JsonAlias("feeDetail")
        private List<Map<String, String>> feeDetail = new ArrayList<>();
        private final Map<String, Object> extraFields = new LinkedHashMap<>();

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

        public List<Map<String, String>> getFeeDetail() {
            return feeDetail;
        }

        @JsonAlias("slab_rows")
        public void setFeeDetail(List<? extends Map<String, ?>> feeDetail) {
            this.feeDetail = normalizeRowList(feeDetail);
        }

        @JsonIgnore
        public List<Map<String, String>> getSlabRows() {
            return feeDetail;
        }

        @JsonIgnore
        public void setSlabRows(List<Map<String, String>> slabRows) {
            this.feeDetail = slabRows;
        }

        @JsonAnySetter
        public void putExtraField(String key, Object value) {
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }

        private List<Map<String, String>> normalizeRowList(List<? extends Map<String, ?>> rows) {
            List<Map<String, String>> normalized = new ArrayList<>();
            if (rows == null || rows.isEmpty()) {
                return normalized;
            }
            for (Map<String, ?> row : rows) {
                Map<String, String> normalizedRow = new LinkedHashMap<>();
                if (row != null) {
                    for (Map.Entry<String, ?> entry : row.entrySet()) {
                        normalizedRow.put(entry.getKey(), stringify(entry.getValue()));
                    }
                }
                normalized.add(normalizedRow);
            }
            return normalized;
        }

        private String stringify(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EligibilityCriteriaDto {
        @JsonAlias("gender")
        private String gender;
        @JsonAlias("minimumAge")
        private String minimumAge;
        @JsonAlias("maximumAge")
        private String maximumAge;
        @JsonAlias("ageAsOn")
        private String ageAsOn;
        private String qualification;
        @JsonAlias("residencyRequirement")
        private String residencyRequirement;
        @JsonAlias("postWiseQualification")
        private Map<String, String> postWiseQualification = new LinkedHashMap<>();
        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

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

        public String getResidencyRequirement() {
            return residencyRequirement;
        }

        public void setResidencyRequirement(String residencyRequirement) {
            this.residencyRequirement = residencyRequirement;
        }

        public Map<String, String> getPostWiseQualification() {
            return postWiseQualification;
        }

        public void setPostWiseQualification(Map<String, ?> postWiseQualification) {
            this.postWiseQualification = new LinkedHashMap<>();
            if (postWiseQualification == null || postWiseQualification.isEmpty()) {
                return;
            }

            for (Map.Entry<String, ?> entry : postWiseQualification.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                this.postWiseQualification.put(key, stringifyPostWiseValue(value));
            }
        }

        private String stringifyPostWiseValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof List<?> listValue) {
                return listValue.stream()
                        .map(item -> item == null ? "" : String.valueOf(item).trim())
                        .filter(item -> !item.isBlank())
                        .collect(Collectors.joining("\n"));
            }
            return String.valueOf(value);
        }

        @JsonAnySetter
        public void putExtraField(String key, Object value) {
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VacancyDetailsDto {
        @JsonAlias("totalVacancy")
        private String totalVacancy;
        @JsonAlias("postWise")
        private Map<String, String> postWise = new LinkedHashMap<>();
        @JsonAlias("categoryWise")
        private Map<String, String> categoryWise = new LinkedHashMap<>();
        @JsonAlias("tableRows")
        private List<Map<String, String>> tableRows = new ArrayList<>();
        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        public String getTotalVacancy() {
            return totalVacancy;
        }

        public void setTotalVacancy(Object totalVacancy) {
            this.totalVacancy = totalVacancy == null ? null : String.valueOf(totalVacancy);
        }

        public Map<String, String> getPostWise() {
            return postWise;
        }

        public void setPostWise(Map<String, ?> postWise) {
            this.postWise = normalizeStringMap(postWise);
        }

        public Map<String, String> getCategoryWise() {
            return categoryWise;
        }

        public void setCategoryWise(Map<String, ?> categoryWise) {
            this.categoryWise = normalizeStringMap(categoryWise);
        }

        public List<Map<String, String>> getTableRows() {
            return tableRows;
        }

        @JsonAlias({"districtWiseVacancy", "district_wise_vacancy"})
        public void setTableRows(List<? extends Map<String, ?>> tableRows) {
            this.tableRows = normalizeRowList(tableRows);
        }

        @JsonAnySetter
        public void putExtraField(String key, Object value) {
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }

        private Map<String, String> normalizeStringMap(Map<String, ?> source) {
            Map<String, String> normalized = new LinkedHashMap<>();
            if (source == null || source.isEmpty()) {
                return normalized;
            }
            for (Map.Entry<String, ?> entry : source.entrySet()) {
                normalized.put(entry.getKey(), stringify(entry.getValue()));
            }
            return normalized;
        }

        private List<Map<String, String>> normalizeRowList(List<? extends Map<String, ?>> rows) {
            List<Map<String, String>> normalized = new ArrayList<>();
            if (rows == null || rows.isEmpty()) {
                return normalized;
            }
            for (Map<String, ?> row : rows) {
                Map<String, String> normalizedRow = new LinkedHashMap<>();
                if (row != null) {
                    for (Map.Entry<String, ?> entry : row.entrySet()) {
                        normalizedRow.put(entry.getKey(), stringify(entry.getValue()));
                    }
                }
                normalized.add(normalizedRow);
            }
            return normalized;
        }

        private String stringify(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
