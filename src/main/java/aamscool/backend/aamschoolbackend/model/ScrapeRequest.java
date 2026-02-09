package aamscool.backend.aamschoolbackend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scrape_request")
public class ScrapeRequest {

	@Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
	
    private String url;

    private String itemSelector;

    private String titleSelector;

    private String linkSelector;

    private int fetchLimit;
   
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getItemSelector() {
		return itemSelector;
	}

	public void setItemSelector(String itemSelector) {
		this.itemSelector = itemSelector;
	}

	public String getTitleSelector() {
		return titleSelector;
	}

	public void setTitleSelector(String titleSelector) {
		this.titleSelector = titleSelector;
	}

	public String getLinkSelector() {
		return linkSelector;
	}

	public void setLinkSelector(String linkSelector) {
		this.linkSelector = linkSelector;
	}

	public int getFetchLimit() {
		return fetchLimit;
	}

	public void setFetchLimit(int limit) {
		this.fetchLimit = limit;
	}
    
    
}
