package aamscool.backend.aamschoolbackend.controllers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.service.JobsService;



@RestController
@RequestMapping("/api/jobs")
public class JobsController {

	private final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	JobsService jobsService;
	
	@GetMapping("/latestjobs/{label}")
	public List<HomePageLinksModel> getLatestJob(@PathVariable("label") String label) {
		List<HomePageLinksModel> jobs = new ArrayList<HomePageLinksModel>();

		jobs = ScrapeCache.dataCache.get(label,
				key -> Optional.ofNullable(jobsService.getLatestJob(key)).orElse(Collections.emptyList()));

		return jobs;
	}

	@GetMapping("/jobbyid/{id}")
	public Optional<JobPosts> getPost(@PathVariable("id") long id) {
		JobPosts cached = ScrapeCache.jsondata.getIfPresent(id);
		if (cached != null) {
			return Optional.of(cached);
		}
		Optional<JobPosts> postOpt = jobsService.getPost(id);
		postOpt.ifPresent(post -> ScrapeCache.jsondata.put(id, post));
		return postOpt;
	}
	@GetMapping("/job/{slugWithId}")
	public Optional<JobPosts> getPost(@PathVariable("slugWithId") String slugWithId) {

	    // extract ID from end of slug
	    long id = extractIdFromSlug(slugWithId);

	    if (id == -1) {
	        return Optional.empty();
	    }

	    // check cache first
	    JobPosts cached = ScrapeCache.jsondata.getIfPresent(id);
	    if (cached != null) {
	        return Optional.of(cached);
	    }

	    Optional<JobPosts> postOpt = jobsService.getPost(id);

	    // put into cache
	    postOpt.ifPresent(post -> ScrapeCache.jsondata.put(id, post));

	    return postOpt;
	}

	
	@PostMapping("/savepost")
    public ResponseEntity<Map<String, Object>> savePostData(
            @RequestBody String rawJson,
            @RequestParam(required = false) String lable) {

        try {
        	ScrapeCache.dataCache.invalidate(lable);
            // Parse incoming JSON
            JsonNode root = mapper.readTree(rawJson);

            // ✅ Extract core fields safely
            String title = root.path("title").asText("Untitled Job");
            String advertisementNo = root.path("advertisement_no").asText("NA");
            String label = (lable != null && !lable.isEmpty())
                    ? lable
                    : root.path("post_name").asText(title);

            // ✅ Create job entity
            JobPosts job = new JobPosts();
            job.setLabel(label);
            job.setTitle(title);
            job.setAdvertisementNo(advertisementNo);
            job.setCreatedAt(LocalDate.now());
            job.setContent(rawJson);
            job.setApproved(false);

            // ✅ Save via service
            jobsService.savePost(job);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "✅ Job imported successfully",
                    "jobId", job.getJobId(),
                    "title", job.getTitle(),
                    "advertisementNo", job.getAdvertisementNo()
            ));

        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "❌ Invalid JSON format: " + e.getOriginalMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "❌ Failed to save job: " + e.getMessage()
            ));
        }
    }

	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> updateJob(
			@PathVariable("id") long id,
			@RequestBody JsonNode payload) {
		try {
			JobPosts updates = parseJobFromPayload(payload);
			Optional<JobPosts> updated = jobsService.updateJob(id, updates);
			if (updated.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			ScrapeCache.jsondata.invalidate(id);
			String label = updated.get().getLabel();
			if (label != null) {
				ScrapeCache.dataCache.invalidate(label);
			}
			return ResponseEntity.ok(Map.of(
					"status", "success",
					"message", "Job updated",
					"jobId", updated.get().getJobId()
			));
		} catch (JsonProcessingException e) {
			return ResponseEntity.badRequest().body(Map.of(
					"status", "error",
					"message", "Invalid JSON format: " + e.getOriginalMessage()
			));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of(
					"status", "error",
					"message", "Failed to update job: " + e.getMessage()
			));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable("id") long id) {
		boolean deleted = jobsService.deleteJob(id);
		if (!deleted) {
			return ResponseEntity.notFound().build();
		}
		ScrapeCache.jsondata.invalidate(id);
		return ResponseEntity.ok(Map.of(
				"status", "success",
				"message", "Job deleted"
		));
	}
	
	private long extractIdFromSlug(String slug) {
	    try {
	        // last '-' ke baad jo number hai wahi id hai
	        String idStr = slug.substring(slug.lastIndexOf("-") + 1);
	        return Long.parseLong(idStr);
	    } catch (Exception e) {
	        return -1;
	    }
	}

	private JobPosts parseJobFromPayload(JsonNode payload) throws JsonProcessingException {
		JobPosts job = new JobPosts();
		if (payload == null || payload.isNull()) {
			return job;
		}
		String title = getTextOrNull(payload, "title");
		String advertisementNo = getTextOrNull(payload, "advertisement_no");
		if (advertisementNo == null) {
			advertisementNo = getTextOrNull(payload, "advertisementNo");
		}
		String label = getTextOrNull(payload, "label");
		String createdAtText = getTextOrNull(payload, "createdAt");
		String content = extractContentJson(payload);
		if (title != null) {
			job.setTitle(title);
		}
		if (advertisementNo != null) {
			job.setAdvertisementNo(advertisementNo);
		}
		if (label != null) {
			job.setLabel(label);
		}
		if (createdAtText != null) {
			try {
				job.setCreatedAt(LocalDate.parse(createdAtText));
			} catch (Exception ex) {
				// ignore invalid date
			}
		}
		if (content != null) {
			job.setContent(content);
		}
		return job;
	}

	private String extractContentJson(JsonNode payload) throws JsonProcessingException {
		JsonNode contentNode = payload.get("content");
		if (contentNode == null || contentNode.isNull()) {
			return null;
		}
		if (contentNode.isTextual()) {
			String text = contentNode.asText("");
			if (text.isBlank()) {
				return null;
			}
			mapper.readTree(text);
			return text;
		}
		return mapper.writeValueAsString(contentNode);
	}

	private String getTextOrNull(JsonNode payload, String field) {
		JsonNode node = payload.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		String text = node.asText();
		return text != null && !text.isBlank() ? text : null;
	}

}
