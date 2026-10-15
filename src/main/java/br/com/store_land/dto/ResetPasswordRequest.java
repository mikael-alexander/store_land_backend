package br.com.store_land.dto;

public record ResetPasswordRequest(String token, String password) {}
