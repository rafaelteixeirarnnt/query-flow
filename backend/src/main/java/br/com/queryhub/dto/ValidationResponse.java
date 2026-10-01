package br.com.queryhub.dto;

public record ValidationResponse(boolean valid, String message, String code) {}
