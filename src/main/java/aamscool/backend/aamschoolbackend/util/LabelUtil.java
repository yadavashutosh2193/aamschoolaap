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

}
