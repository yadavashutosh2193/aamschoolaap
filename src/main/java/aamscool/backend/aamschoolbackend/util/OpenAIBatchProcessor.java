package aamscool.backend.aamschoolbackend.util;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.controllers.ScraperScheduler;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.service.JobsService;
import aamscool.backend.aamschoolbackend.service.OpenAIService;
@Component
public class OpenAIBatchProcessor {

	@Autowired
    private OpenAIService openAiService;
	
	@Autowired
	JobsService jobsService;
	private final ObjectMapper mapper = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);
    /* ==================================
       MAIN PROCESS METHOD
    ================================== */

    public void processAndUpload(List<Map<String, Object>> jobs,String baseLink)
            throws Exception {

            try {
            	// 2️⃣ Sort by post date
            	PostDateSorter.sortByPostDate(jobs);

            	// 3️⃣ Save
                String aiJson = "";
            	for (Map<String, Object> job : jobs) {
                    
                    	System.out.println(job);
						if (null != job) {
							aiJson = openAiService.processJobMap(job);
							//aiJson = JsonRepairUtil.fixBrokenJson(aiJson);

							aiJson = openAiService.extractCleanJson(aiJson);
						
							aiJson = SarkariLinkCleaner.cleanLinks(aiJson);
							System.out.println(aiJson);

							String label = LabelUtil.extractLabel(baseLink);

							ResponseEntity<Map<String, Object>> responseResult = savePostData(aiJson, label);
						}
            	    Thread.sleep(3000);
            	}

                
            } catch (Exception e) {

                System.err.println(
                        "❌ Failed: "
                );

                e.printStackTrace();
            }

        System.out.println("✅ All Jobs Uploaded");
    }
    public ResponseEntity<Map<String, Object>> savePostData(String rawJson, String lable) {

        try {
        	ScrapeCache.dataCache.invalidate(lable);
        	log.info("saving json into db label = " + lable);
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

