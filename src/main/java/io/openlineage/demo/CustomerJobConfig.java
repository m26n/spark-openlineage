package io.openlineage.demo;

import java.util.HashMap;
import java.util.Map;

final class CustomerJobConfig {
    private final String appName;
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;
    private final String sourceTable;
    private final String outputPath;
    private final String s3Endpoint;
    private final String s3AccessKey;
    private final String s3SecretKey;

    private CustomerJobConfig(
            String appName,
            String jdbcUrl,
            String jdbcUser,
            String jdbcPassword,
            String sourceTable,
            String outputPath,
            String s3Endpoint,
            String s3AccessKey,
            String s3SecretKey) {
        this.appName = appName;
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
        this.sourceTable = sourceTable;
        this.outputPath = outputPath;
        this.s3Endpoint = s3Endpoint;
        this.s3AccessKey = s3AccessKey;
        this.s3SecretKey = s3SecretKey;
    }

    static CustomerJobConfig fromArgs(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Arguments must use the form --key=value. Invalid argument: " + arg);
            }
            String[] parts = arg.substring(2).split("=", 2);
            values.put(parts[0], parts[1]);
        }

        return new CustomerJobConfig(
                values.getOrDefault("app-name", "customer-curation-demo"),
                values.getOrDefault("jdbc-url", "jdbc:postgresql://postgres-source:5432/customerdb"),
                values.getOrDefault("jdbc-user", "customers"),
                values.getOrDefault("jdbc-password", "customers"),
                values.getOrDefault("source-table", "public.customers"),
                values.getOrDefault("output-path", "s3a://customer-lake/customers_curated/"),
                values.getOrDefault("s3-endpoint", "http://minio:9000"),
                values.getOrDefault("s3-access-key", "minioadmin"),
                values.getOrDefault("s3-secret-key", "minioadmin"));
    }

    String appName() {
        return appName;
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    String jdbcUser() {
        return jdbcUser;
    }

    String jdbcPassword() {
        return jdbcPassword;
    }

    String sourceTable() {
        return sourceTable;
    }

    String outputPath() {
        return outputPath;
    }

    String s3Endpoint() {
        return s3Endpoint;
    }

    String s3AccessKey() {
        return s3AccessKey;
    }

    String s3SecretKey() {
        return s3SecretKey;
    }
}

