package com.steadyteller.backend.global.bootstrap;

import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update does not reliably relax an existing MySQL NOT NULL
 * constraint. Non-task calendar entries (break, supplement, empty slot) need a
 * nullable learning_task_id, so upgrade existing local databases once at startup.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduleItemSchemaMigrator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateMySqlOnly() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
            Integer required = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'schedule_item'
                      AND column_name = 'learning_task_id'
                      AND is_nullable = 'NO'
                    """, Integer.class);
            if (required != null && required > 0) {
                jdbcTemplate.execute("ALTER TABLE schedule_item MODIFY COLUMN learning_task_id BIGINT NULL");
                log.info("Migrated schedule_item.learning_task_id to nullable for non-task calendar slots.");
            }
        } catch (Exception exception) {
            // A missing table is normal on a newly created schema; Hibernate creates it.
            log.warn("Could not verify schedule_item nullable task column: {}", exception.getMessage());
        }
    }
}
