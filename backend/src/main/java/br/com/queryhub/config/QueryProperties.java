package br.com.queryhub.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("query")
@Validated
public record QueryProperties(
    @Min(1) @Max(300) int timeoutSeconds,
    @Min(1) @Max(Integer.MAX_VALUE - 1) int maxResultRows,
    @Min(1) @Max(20) int maxConcurrentDatabases,
    @Min(1) @Max(4) int maxActiveExecutions,
    @Min(1) @Max(50) int maxDatabasesPerQuery,
    @Min(1) int admissionTimeoutSeconds,
    @Min(1) int connectTimeoutSeconds,
    @Min(1024) @Max(1048576) int maxCellBytes,
    @Min(1024) @Max(33554432) long maxExecutionBytes) {}
