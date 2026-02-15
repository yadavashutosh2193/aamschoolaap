package aamscool.backend.aamschoolbackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;

@Repository
public interface JobsRepository extends JpaRepository<JobPosts, Long>{

//	@Query("SELECT new com.aamschool.aamschoolapp.model.HomePageLinksModel(j.title,j.jobId) FROM JobPost j WHERE j.label = 'Latest Jobs'")
//	List<HomePageLinksModel> findJobIdandTitleByLabel();

	@Query("SELECT new aamscool.backend.aamschoolbackend.model.HomePageLinksModel(j.title,j.jobId,j.createdAt) FROM JobPosts j WHERE j.label =:label")
	List<HomePageLinksModel> getJobIdandTile(@Param("label") String label);

	Optional<JobPosts> findByLabelAndAdvertisementNo(String label, String advertisementNo);

	@Query("SELECT j.jobId FROM JobPosts j ORDER BY j.jobId DESC")
    List<Long> findAllJobIds();
}
