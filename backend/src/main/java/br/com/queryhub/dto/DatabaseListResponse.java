package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record DatabaseListResponse(List<String> databases) {}
