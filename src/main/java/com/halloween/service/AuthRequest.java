package com.halloween.service;

public record AuthRequest(
        String email,
        String password
) {
}
