package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.controllers.ScraperScheduler;
import aamscool.backend.aamschoolbackend.model.Notification;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.util.OpenAIBatchProcessor;

@Service
public class GenericScraperService {

	@Autowired
	private ScrapeCache cache;
	@Autowired
	OpenAIBatchProcessor openAiBatch;
	
	private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);
	
	public List<Map<String, Object>> scrape(String url, String itemSelector, String titleSelector, String linkSelector,int limit) {
		List<Map<String, Object>> response = new ArrayList<>();
		if(null != url) {
		try {

			Document doc = Jsoup.connect(url).timeout(10000).get();

			Elements items = doc.select(itemSelector);
            int count = 0;
			for (Element item : items) {
                if(count >= limit)
                	break;
				String title = item.select(titleSelector).text();
				String link = item.select(linkSelector).attr("href");

					Notification n = new Notification();

					n.setTitle(title);
					n.setLink(link);
					n.setScrapedDate(LocalDate.now());
					count++;
					System.out.println(n.getTitle() + " count = " + count);
                     log.info("scrapped link with title " + n.getTitle());
					if (!cache.isProcessed(n.getLink())) {
						Map<String, Object> result = scrape(n.getLink());
						System.out.println(result);
						response.add(result);
						cache.markProcessed(n.getLink());
					}
			}

		} catch (Exception e) {
			log.info("exception occured " + e.getMessage());
			e.printStackTrace();
		}
		
		try {
			if(response != null && response.size() > 0) {
			openAiBatch.processAndUpload(response,url);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.info("exception occured in processAndUpload method" + e.getMessage());
			e.printStackTrace();
		}
	}
		
		return response;
	}

	/*
	 * =============================== CONFIG ===============================
	 */

	// Pattern: Key Value
	private static final Pattern KV_PATTERN = Pattern.compile("(.+?)\\s{2,}(.+)");

	// Noise words
	private static final List<String> BLOCK_WORDS = List.of("telegram", "whatsapp", "latest posts", "related posts",
			"disclaimer", "download app", "follow now");

	/*
	 * =============================== MAIN API ===============================
	 */

	public Map<String, Object> scrape(String url) {

		Map<String, Object> result = new LinkedHashMap<>();

		try {

			/* ---------- Load Page ---------- */

			Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(20000).get();

			/* ---------- Main Content ---------- */

			Element main = doc.selectFirst("#post, .entry-content, article, .container");

			if (main == null)
				main = doc.body();

			/* ---------- Meta ---------- */

			result.put("pageTitle", doc.title());
			result.put("url", url);

			/* ---------- Parse ---------- */

			Map<String, Object> content = parse(main);

			result.put("content", content);

		} catch (Exception e) {

			e.printStackTrace();
			result.put("error", "Scraping failed");
		}

		return result;
	}

	/*
	 * =============================== CORE PARSER ===============================
	 */

	private Map<String, Object> parse(Element root) {

		Map<String, Object> json = new LinkedHashMap<>();

		String sectionName = "General";

		Map<String, Object> section = new LinkedHashMap<>();

		json.put(sectionName, section);

		for (Element el : root.select("*")) {

			String tag = el.tagName();

			String text = clean(el.text());

			/* ---------- Skip Noise ---------- */

			if (isNoise(text))
				continue;

			/* ---------- Headings ---------- */

			if (tag.matches("h[1-6]")) {

				if (isBadSection(text))
					continue;

				sectionName = text;

				section = new LinkedHashMap<>();

				json.put(sectionName, section);

				continue;
			}

			/* ---------- Tables ---------- */

			if (tag.equals("table")) {

				List<Map<String, String>> rows = parseTableWithLinks(el);

				if (!rows.isEmpty()) {

					section.put("rows", rows);
				}

				continue;
			}

			/* ---------- Links ---------- */

			if (tag.equals("a")) {

				String href = el.absUrl("href");

				if (!href.isEmpty() && !text.isEmpty() && !text.equalsIgnoreCase("click here")) {

					section.put(text, href);
				}

				continue;
			}

			/* ---------- Text ---------- */

			if (tag.equals("p") || tag.equals("li")) {

				extractKeyValue(text, section);
			}
		}

		/* ---------- Cleanup ---------- */

		return cleanSections(json);
	}

	/*
	 * =============================== TEXT PARSER ===============================
	 */

	private void extractKeyValue(String text, Map<String, Object> section) {

		if (text.isEmpty())
			return;

		Matcher m = KV_PATTERN.matcher(text);

		// "Key Value"
		if (m.matches()) {

			String key = clean(m.group(1));
			String val = clean(m.group(2));

			if (!key.isEmpty() && !val.isEmpty()) {

				section.put(key, val);
			}

		} else {

			addToList(section, text);
		}
	}

	private void addToList(Map<String, Object> section, String text) {

		if (text.length() < 4)
			return;

		List<String> list;

		if (section.containsKey("list")) {

			list = (List<String>) section.get("list");

		} else {

			list = new ArrayList<>();

			section.put("list", list);
		}

		list.add(text);
	}

	/*
	 * =============================== TABLE PARSER ===============================
	 */

	private List<Map<String, String>> parseTableWithLinks(Element table) {

		List<Map<String, String>> list = new ArrayList<>();

		Elements rows = table.select("tr");

		if (rows.size() < 2)
			return list;

		for (int i = 1; i < rows.size(); i++) {

			Elements cols = rows.get(i).select("td");

			if (cols.size() < 2)
				continue;

			String left = clean(cols.get(0).text());

			Element link = cols.get(1).selectFirst("a[href]");

			String right;

			if (link != null) {

				right = link.absUrl("href");

			} else {

				right = clean(cols.get(1).text());
			}

			if (!left.isEmpty() && !right.isEmpty()) {

				Map<String, String> row = new LinkedHashMap<>();

				row.put(left, right);

				list.add(row);
			}
		}

		return list;
	}

	/*
	 * =============================== CLEANUP ===============================
	 */

	private Map<String, Object> cleanSections(Map<String, Object> data) {

		Map<String, Object> clean = new LinkedHashMap<>();

		for (Map.Entry<String, Object> e : data.entrySet()) {

			if (!(e.getValue() instanceof Map))
				continue;

			Map<String, Object> section = (Map<String, Object>) e.getValue();

			if (section.isEmpty())
				continue;

			if (shouldDropSection(e.getKey(), section))
				continue;

			clean.put(e.getKey(), section);
		}

		return clean;
	}

	/*
	 * =============================== FILTERS ===============================
	 */

	private boolean isNoise(String text) {

		if (text == null)
			return true;

		String t = text.toLowerCase();

		for (String w : BLOCK_WORDS) {

			if (t.contains(w))
				return true;
		}

		return false;
	}

	private boolean isBadSection(String text) {

		if (text == null || text.length() < 4)
			return true;

		String t = text.toLowerCase();

		return t.contains("important question") || t.contains("latest") || t.contains("related") || t.contains("faq")
				|| t.contains("result.com.cm") || t.contains("click here");
	}

	/*
	 * =============================== UTILS ===============================
	 */

	private String clean(String text) {

		return text.replaceAll("\\s+", " ").replace(":", "").trim();
	}

	private boolean shouldDropSection(String name, Map<String, Object> section) {

		String t = name.toLowerCase();

		if (t.contains("official website"))
			return true;
		if (t.contains("total post"))
			return true;
		if (t.contains("question"))
			return true;
		if (t.contains("faq"))
			return true;

// If only list with unrelated jobs
		if (section.containsKey("list")) {

			List<?> list = (List<?>) section.get("list");

			long jobLinks = list.stream().filter(o -> o.toString().contains("202")).count();

			if (jobLinks > 5)
				return true;
		}

		return false;
	}

}
