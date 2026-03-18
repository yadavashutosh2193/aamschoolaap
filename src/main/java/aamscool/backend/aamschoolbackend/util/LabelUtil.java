package aamscool.backend.aamschoolbackend.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class LabelUtil {

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
              return "latest-results";
          }

          if (url.contains("/admit-card")) {
              return "admit-cards";
          }

          if (url.contains("/latest-jobs")) {
              return "latest-jobs";
          }

          if (url.contains("/answer-key")) {
              return "answer-keys";
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
            return "latest-jobs";
        }
        if (normalized.contains("admit")) {
            return "admit-cards";
        }
        if (normalized.contains("result")) {
            return "latest-results";
        }
        if (normalized.contains("answer")) {
            return "answer-keys";
        }
        if (normalized.contains("admission")) {
            return "admission";
        }
        if (normalized.contains("document")) {
            return "documents";
        }

        String slug = categoryName.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        return slug.isBlank() ? "job" : slug;
    }

    public static List<String> buildLabelLookupCandidates(String label) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String requested = label == null ? "" : label.trim();
        String normalized = normalizeCategoryLabel(requested);

        candidates.add(normalized);
        if (!requested.isBlank()) {
            candidates.add(requested);
            candidates.add(requested.replace('-', ' '));
        }
        candidates.add(normalized.replace('-', ' '));

        String legacyDisplay = legacyDisplayFromSlug(normalized);
        if (legacyDisplay != null && !legacyDisplay.isBlank()) {
            candidates.add(legacyDisplay);
        }

        return new ArrayList<>(candidates);
    }

    private static String legacyDisplayFromSlug(String slug) {
        return switch (slug) {
            case "latest-jobs" -> "Latest jobs";
            case "admit-cards" -> "admit cards";
            case "latest-results" -> "Latest results";
            case "answer-keys" -> "Answer Keys";
            case "admission" -> "admission";
            case "documents" -> "documents";
            default -> null;
        };
    }

}
