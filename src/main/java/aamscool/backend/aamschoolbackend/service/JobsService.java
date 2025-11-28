package aamscool.backend.aamschoolbackend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
