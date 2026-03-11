package aamscool.backend.aamschoolbackend.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class JobMasterSchemaFixConfig {

    private static final Logger log = LoggerFactory.getLogger(JobMasterSchemaFixConfig.class);

    @Bean
    public CommandLineRunner jobMasterSchemaFixRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            List<String> alters = List.of(
                    "ALTER TABLE job_master MODIFY COLUMN source VARCHAR(2048) NULL",
                    "ALTER TABLE job_master MODIFY COLUMN title LONGTEXT NULL",
                    "ALTER TABLE job_master MODIFY COLUMN short_description LONGTEXT NULL",
                    "ALTER TABLE job_master MODIFY COLUMN post_name LONGTEXT NULL",
                    "ALTER TABLE job_master MODIFY COLUMN conducting_body LONGTEXT NULL",
                    "ALTER TABLE job_master MODIFY COLUMN pay_scale LONGTEXT NULL",
                    "ALTER TABLE job_master MODIFY COLUMN important_dates JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN application_fee JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN eligibility_criteria JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN vacancy_details JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN application_process JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN exam_scheme JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN selection_process JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN important_notes JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN official_links JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN syllabus_overview JSON NULL",
                    "ALTER TABLE job_master MODIFY COLUMN other_tables JSON NULL"
            );

            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'job_master'",
                        Integer.class
                );
                if (count == null || count == 0) {
                    return;
                }
            } catch (Exception ex) {
                log.warn("Unable to verify job_master table existence", ex);
                return;
            }

            for (String sql : alters) {
                try {
                    jdbcTemplate.execute(sql);
                } catch (Exception ex) {
                    log.warn("Schema fix statement failed: {}", sql);
                }
            }
        };
    }
}
