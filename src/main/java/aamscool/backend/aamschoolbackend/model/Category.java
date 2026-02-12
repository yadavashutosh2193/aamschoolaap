package aamscool.backend.aamschoolbackend.model;

import java.util.List;

public class Category {

    private String name;
    private String categoryUrl;
    private List<Post> posts;

    public Category(String name, String categoryUrl, List<Post> posts) {
        this.name = name;
        this.categoryUrl = categoryUrl;
        this.posts = posts;
    }

    public String getName() {
        return name;
    }

    public String getCategoryUrl() {
        return categoryUrl;
    }

    public List<Post> getPosts() {
        return posts;
    }
}