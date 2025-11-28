package aamscool.backend.aamschoolbackend.model;

import java.time.LocalDate;

public class HomePageLinksModel {
	private String title;
	private long id;
	private LocalDate postDate;
	
	
	public HomePageLinksModel(String title, long id, LocalDate postDate) {
		super();
		this.title = title;
		this.id = id;
		this.postDate = postDate;
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
	
	
}
