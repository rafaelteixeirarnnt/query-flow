package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ConnectionTestResponse(boolean success, String message, long responseTimeMs) {}
