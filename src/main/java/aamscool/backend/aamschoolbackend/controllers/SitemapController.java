package aamscool.backend.aamschoolbackend.controllers;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.repository.JobsRepository;

@RestController
public class SitemapController {

    @Autowired
    JobsRepository jobDao;

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public String generateSitemap() {

        String baseUrl = "https://www.aamschool.in";

        List<HomePageLinksModel> jobs = jobDao.findAllJobTitlesAndIds();
        if (jobs == null) jobs = List.of();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");

        // formatter for lastmod
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // ⭐ Homepage
        xml.append("<url>");
        xml.append("<loc>").append(baseUrl).append("</loc>");
        xml.append("<changefreq>hourly</changefreq>");
        xml.append("<priority>1.0</priority>");
        xml.append("</url>");

        // ⭐ Main sections
        addStaticUrl(xml, baseUrl + "/latest-jobs", "hourly", "0.9");
        addStaticUrl(xml, baseUrl + "/admit-card", "hourly", "0.9");
        addStaticUrl(xml, baseUrl + "/results", "hourly", "0.9");
        addStaticUrl(xml, baseUrl + "/answer-key", "daily", "0.8");
        addStaticUrl(xml, baseUrl + "/syllabus", "daily", "0.8");
        addStaticUrl(xml, baseUrl + "/admission", "daily", "0.8");

        // ⭐ Job detail pages
        for (HomePageLinksModel job : jobs) {

            String slug = toSlug(job.getTitle());
            Long id = job.getId();

            xml.append("<url>");
            xml.append("<loc>")
               .append(baseUrl)
               .append("/job/")
               .append(slug)
               .append("-")
               .append(id)
               .append("</loc>");

            // lastmod from DB created date
            if (job.getPostDate() != null) {
                xml.append("<lastmod>")
                   .append(job.getPostDate().format(formatter))
                   .append("</lastmod>");
            }

            xml.append("<changefreq>daily</changefreq>");
            xml.append("<priority>0.8</priority>");
            xml.append("</url>");
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    // helper for static urls
    private void addStaticUrl(StringBuilder xml, String url, String freq, String priority) {
        xml.append("<url>");
        xml.append("<loc>").append(url).append("</loc>");
        xml.append("<changefreq>").append(freq).append("</changefreq>");
        xml.append("<priority>").append(priority).append("</priority>");
        xml.append("</url>");
    }

    // slug generator
    private String toSlug(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain")
    public String robots() {
        return "User-agent: *\nAllow: /\nSitemap: https://www.aamschool.in/sitemap.xml";
    }
}

