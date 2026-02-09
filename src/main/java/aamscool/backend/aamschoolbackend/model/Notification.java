package aamscool.backend.aamschoolbackend.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "notifications")
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String link;

    private String source;

    private LocalDate scrapedDate;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public LocalDate getScrapedDate() {
		return scrapedDate;
	}

	public void setScrapedDate(LocalDate scrapedDate) {
		this.scrapedDate = scrapedDate;
	}

	@Override
	public String toString() {
		return "Notification [id=" + id + ", title=" + title + ", link=" + link + ", source=" + source
				+ ", scrapedDate=" + scrapedDate + "]";
	}
    
    
}
