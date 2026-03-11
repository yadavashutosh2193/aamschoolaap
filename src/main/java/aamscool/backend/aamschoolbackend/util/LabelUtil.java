package aamscool.backend.aamschoolbackend.util;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LabelUtil {

    private static final ObjectMapper mapper =
            new ObjectMapper();


    /* ==================================
       AUTO GENERATE LABEL
    ================================== */

    public static String extractLabel(String url)
            throws Exception {

          if (url == null || url.isBlank()) {
              return "job";
          }

    	  url = url.toLowerCase();

          // Apply rules
          if (url.contains("/admission")) {
              return "admission";
          }

          if (url.contains("/result")) {
              return "Latest results";
          }

          if (url.contains("/admit-card")) {
              return "admit cards";
          }

          if (url.contains("/latest-jobs")) {
              return "Latest jobs";
          }

          if (url.contains("/answer-key")) {
              return "Answer Keys";
          }

          // Default
          return "job";
      }

    public static String normalizeCategoryLabel(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return "job";
        }

        String normalized = categoryName.toLowerCase().trim();
        normalized = normalized.replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();

        if (normalized.contains("latest") && normalized.contains("job")) {
            return "Latest jobs";
        }
        if (normalized.contains("admit")) {
            return "admit cards";
        }
        if (normalized.contains("result")) {
            return "Latest results";
        }
        if (normalized.contains("answer")) {
            return "Answer Keys";
        }
        if (normalized.contains("admission")) {
            return "admission";
        }
        if (normalized.contains("document")) {
            return "documents";
        }

        return categoryName.trim();
    }

}
