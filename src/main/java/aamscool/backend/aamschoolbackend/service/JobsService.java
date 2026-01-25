package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.repository.JobsRepository;

@Service
public class JobsService {

	@Autowired
	JobsRepository jobDao;
	
	public Optional<JobPosts> getPost(long id) {
		return jobDao.findById(id);
		
	}
	public List<HomePageLinksModel> getLatestJob(String label) {
		List<HomePageLinksModel> links = jobDao.getJobIdandTile(label);
		links = links.stream().sorted(Comparator.comparing(HomePageLinksModel::getPostDate).reversed()).toList();
		return links;
	}
	
	@Transactional
    public JobPosts savePost(JobPosts job) {
        try {
            // Check if both label and advertisementNo match an existing record
            Optional<JobPosts> existing = Optional.empty();

            if (job.getLabel() != null && job.getAdvertisementNo() != null &&
                !job.getLabel().isBlank() && !job.getAdvertisementNo().isBlank()) {
                existing = jobDao.findByLabelAndAdvertisementNo(job.getLabel(), job.getAdvertisementNo());
            }

            if (existing.isPresent()) {
                JobPosts old = existing.get();

                // ✅ Update existing record
                old.setTitle(job.getTitle());
                old.setContent(job.getContent());
                old.setCreatedAt(LocalDate.now());
                old.setApproved(false); // Reset approval since content changed
                return jobDao.save(old);

            } else {
                // ✅ Create new record
                job.setCreatedAt(LocalDate.now());
                job.setApproved(false);
                return jobDao.save(job);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save job post: " + e.getMessage(), e);
        }
    }

}
