package aamscool.backend.aamschoolbackend.model;

import java.time.LocalDate;

public class QuizListItem {
    private String title;
    private long id;
    private LocalDate postDate;
    private String slugurl;

    public QuizListItem(String title, long id, LocalDate postDate) {
        this.title = title;
        this.id = id;
        this.postDate = postDate;
        this.slugurl = toSlug(this.title) + "-" + this.id;
    }

    public String getTitle() {
        return title;
    }

    public long getId() {
        return id;
    }

    public LocalDate getPostDate() {
        return postDate;
    }

    public String getSlugurl() {
        return slugurl;
    }

    private String toSlug(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}
