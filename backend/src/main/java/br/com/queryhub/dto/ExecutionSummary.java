package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ExecutionSummary(
    int selectedDatabases, int successCount, int errorCount, long rowCount) {}
