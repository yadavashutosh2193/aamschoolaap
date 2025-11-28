package aamscool.backend.aamschoolbackend.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.service.JobsService;

@RestController
public class JobsController {

	@Autowired
	JobsService jobsService;
	
	@GetMapping("/latestjobs/{label}")
    public List<HomePageLinksModel> getLatestJob(@PathVariable("label") String label) {
		List<HomePageLinksModel> jobs = new ArrayList<HomePageLinksModel>();
		jobs = jobsService.getLatestJob(label);
        return jobs;
    }
	@GetMapping("/job/{id}")
	public Optional<JobPosts> getPost(@PathVariable("id") long id) {
	return jobsService.getPost(id);
	}
}
