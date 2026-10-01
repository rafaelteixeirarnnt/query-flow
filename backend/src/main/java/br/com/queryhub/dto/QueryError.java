package br.com.queryhub.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record QueryError(String code, String message) {}
