package br.com.queryhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record ExportRequest(@NotNull @Valid ExecutionResponse execution) {}
