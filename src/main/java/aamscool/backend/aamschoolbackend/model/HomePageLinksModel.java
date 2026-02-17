package aamscool.backend.aamschoolbackend.model;

import java.time.LocalDate;

public class HomePageLinksModel {
	private String title;
	private long id;
	private LocalDate postDate;
	private String slugurl;
	
	
	public HomePageLinksModel(String title, long id, LocalDate postDate) {
		super();
		this.title = title;
		this.id = id;
		this.postDate = postDate;
		this.slugurl = toSlug(this.title)+"-"+this.id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public LocalDate getPostDate() {
		return postDate;
	}
	public void setPostDate(LocalDate postDate) {
		this.postDate = postDate;
	}
	
	public String getSlugurl() {
		return slugurl;
	}
	public void setSlugurl(String slugurl) {
		this.slugurl = slugurl;
	}
	// slug generator
    private String toSlug(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
	
}
