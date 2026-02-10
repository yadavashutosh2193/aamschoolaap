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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@CrossOrigin(origins = {
		"http://localhost:3000",
        "http://aamschool-frontend.s3-website.ap-south-1.amazonaws.com"
})
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
	@GetMapping("/job/{id}")
	public Optional<JobPosts> getPost(@PathVariable("id") long id) {
		JobPosts cached = ScrapeCache.jsondata.getIfPresent(id);
	    if (cached != null) {
	        return Optional.of(cached);
	    }

	    Optional<JobPosts> postOpt = jobsService.getPost(id);

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
}
