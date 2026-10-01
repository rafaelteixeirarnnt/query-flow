package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ConnectionRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 1000) String description,
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9.:%_-]+") @Size(max = 253) String host,
    @Min(1) @Max(65535) int port,
    @NotBlank @Size(max = 63) String initialDatabase,
    @NotBlank @Size(max = 128) String username,
    @Size(max = 4096) String password,
    boolean sslEnabled,
    boolean active,
    Long version) {}
