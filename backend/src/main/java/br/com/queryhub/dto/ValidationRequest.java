package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ValidationRequest(@NotBlank @Size(max = 100000) String sql) {}
