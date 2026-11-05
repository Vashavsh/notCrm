package com.pipeline.crm.learn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

/**
 * Creates the course site's schema in the test database from learn-backend's own migrations, so the CRM tests run against
 * the real bridge (the account table, the progress view) and not against a copy that could drift. In production
 * learn-backend creates the schema itself on its first start.
 */
final class LearnBridgeSchema {

    private static final Path MIGRATIONS = Path.of("../learn-backend/src/main/resources/db/migration");
    private static final Pattern VERSION = Pattern.compile("V(\\d+)__.*\\.sql");

    private LearnBridgeSchema() {
    }

    static void install(DataSource dataSource) throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement();
                    var exists = statement.executeQuery("select to_regclass('learn.account') is not null")) {
                exists.next();
                if (exists.getBoolean(1)) {
                    return;
                }
            }
            for (Path migration : migrations()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("create schema if not exists learn");
                    statement.execute("set local search_path to learn");
                    statement.execute(Files.readString(migration));
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }
        }
    }

    private static List<Path> migrations() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.filter(p -> VERSION.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(p -> {
                        Matcher m = VERSION.matcher(p.getFileName().toString());
                        m.matches();
                        return Integer.parseInt(m.group(1));
                    })).toList();
        }
    }
}
