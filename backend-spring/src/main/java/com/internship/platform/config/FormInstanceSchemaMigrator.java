package com.internship.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class FormInstanceSchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(FormInstanceSchemaMigrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public FormInstanceSchemaMigrator(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseName = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT);
            Map<String, String> columnsToAdd = buildColumnDefinitions(databaseName);

            for (Map.Entry<String, String> entry : columnsToAdd.entrySet()) {
                if (columnExists(metaData, "form_instance", entry.getKey())) {
                    continue;
                }
                String sql = "ALTER TABLE form_instance ADD COLUMN " + entry.getKey() + " " + entry.getValue();
                jdbcTemplate.execute(sql);
                log.info("Added missing column {} to form_instance", entry.getKey());
            }
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (var resultSet = metaData.getColumns(null, null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            return resultSet.next();
        }
    }

    private Map<String, String> buildColumnDefinitions(String databaseName) {
        Map<String, String> columns = new LinkedHashMap<>();
        if (databaseName.contains("mysql")) {
            columns.put("modification_reason", "VARCHAR(255)");
            columns.put("modification_review_comment", "VARCHAR(255)");
            columns.put("modification_requested_at", "DATETIME");
            columns.put("modification_reviewed_at", "DATETIME");
            columns.put("history_json", "LONGTEXT");
        } else {
            columns.put("modification_reason", "VARCHAR(255)");
            columns.put("modification_review_comment", "VARCHAR(255)");
            columns.put("modification_requested_at", "TIMESTAMP");
            columns.put("modification_reviewed_at", "TIMESTAMP");
            columns.put("history_json", "CLOB");
        }
        return columns;
    }
}
