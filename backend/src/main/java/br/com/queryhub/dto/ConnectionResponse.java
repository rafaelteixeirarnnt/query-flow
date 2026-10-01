package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record ConnectionResponse(
    long id,
    long version,
    String name,
    String description,
    String host,
    int port,
    String initialDatabase,
    String username,
    boolean sslEnabled,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
