package aamscool.backend.aamschoolbackend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.repository.JobsRepository;

import java.util.List;

@RestController
public class SitemapController {

	@Autowired
	JobsRepository jobDao;

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public String generateSitemap() {

        String baseUrl = "https://aamschool.in";

        List<Long> jobIds = jobDao.findAllJobIds();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");

        // static pages
        xml.append("<url><loc>").append(baseUrl).append("</loc></url>");
        xml.append("<url><loc>").append(baseUrl).append("/latest-jobs</loc></url>");
        xml.append("<url><loc>").append(baseUrl).append("/admit-card</loc></url>");
        xml.append("<url><loc>").append(baseUrl).append("/results</loc></url>");
        xml.append("<url><loc>").append(baseUrl).append("/admission</loc></url>");
        xml.append("<url><loc>").append(baseUrl).append("/answer-key</loc></url>");
        xml.append("<url><loc>").append(baseUrl).append("/syllabus</loc></url>");

        // job detail pages
        for (Long id : jobIds) {
            xml.append("<url>");
            xml.append("<loc>").append(baseUrl).append("/job/").append(id).append("</loc>");
            xml.append("</url>");
        }

        xml.append("</urlset>");

        return xml.toString();
    }
}

