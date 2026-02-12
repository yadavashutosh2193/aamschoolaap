package aamscool.backend.aamschoolbackend.model;

public class Post {

    private String title;
    private String url;

    public Post(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }
}

