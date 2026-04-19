package com.authspring.api.web.dto;

public record LoginResponse(String token, AuthUserResponse user) {}
