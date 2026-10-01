package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ColumnMetadata(
    String key, String label, int jdbcType, String typeName, List<String> sourceTypes) {}
