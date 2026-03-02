package aamscool.backend.aamschoolbackend.dto;

import java.util.List;

public class QuestionBulkUpsertResult {

    private int total;
    private int created;
    private int updated;
    private int duplicates;
    private List<String> createdCodes;
    private List<String> updatedCodes;
    private String message;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }

    public List<String> getCreatedCodes() {
        return createdCodes;
    }

    public void setCreatedCodes(List<String> createdCodes) {
        this.createdCodes = createdCodes;
    }

    public List<String> getUpdatedCodes() {
        return updatedCodes;
    }

    public void setUpdatedCodes(List<String> updatedCodes) {
        this.updatedCodes = updatedCodes;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}