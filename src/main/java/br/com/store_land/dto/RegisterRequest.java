package br.com.store_land.dto;

public record RegisterRequest(
        String fullName,
        String username,
        String email,
        String password,
        String confirmPassword
) {}
