package aamscool.backend.aamschoolbackend.util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import aamscool.backend.aamschoolbackend.model.Category;
import aamscool.backend.aamschoolbackend.model.Post;

public class HomepageScraperService {

    private static final String URL = "https://sarkariresult.com.cm/";

    private static final List<String> CATEGORIES = Arrays.asList(
            "Results",
            "Admit Cards",
            "Latest Jobs",
            "Answer Key",
            "Admission",
            "Documents"
    );

    public static void scrape() throws IOException {

        List<Category> scrapedData = scrapeHomepage();

        for (Category category : scrapedData) {

            System.out.println("\n===== " + category.getName() + " =====");
            System.out.println("Category Link: " + category.getCategoryUrl());

            for (Post post : category.getPosts()) {
                System.out.println(post.getTitle() + " -> " + post.getUrl());
            }
        }
    }

    public static List<Category> scrapeHomepage() throws IOException {

        Document doc = Jsoup.connect(URL)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();

        List<Category> categoryList = new ArrayList<>();

        Elements pTags = doc.select("p");

        for (Element p : pTags) {

            String sectionTitle = p.text().trim();

            for (String categoryName : CATEGORIES) {

                if (sectionTitle.equalsIgnoreCase(categoryName)) {

                    // Get category link from navbar
                    String categoryUrl = extractCategoryUrlFromNavbar(doc, categoryName);

                    // Find UL below this P
                    Element ul = p.nextElementSibling();
                    while (ul != null && !ul.tagName().equals("ul")) {
                        ul = ul.nextElementSibling();
                    }

                    if (ul != null) {

                        List<Post> posts = new ArrayList<>();
                        Elements links = ul.select("a");

                        for (Element link : links) {
                        	if(posts.size() < 2)
                            posts.add(new Post(
                                    link.text().trim(),
                                    link.absUrl("href")
                            ));
                        }

                        categoryList.add(new Category(
                                categoryName,
                                categoryUrl,
                                posts
                        ));
                    }
                }
            }
        }

        return categoryList;
    }
    private static String toSingular(String text) {
        text = text.toLowerCase().trim();
        if (text.endsWith("s")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }
    private static String extractCategoryUrlFromNavbar(Document doc, String categoryName) {

        Elements navLinks = doc.select("nav a");

        for (Element link : navLinks) {
            if (link.text().trim().equalsIgnoreCase(toSingular(categoryName))) {
                return link.absUrl("href");
            }
        }

        return "N/A";
    }
}
