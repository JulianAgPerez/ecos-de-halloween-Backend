package com.halloween.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TokenHasher {

    private static final String ALGORITHM = "SHA-256";

    private TokenHasher() {
    }

    public static String sha256(final String rawToken) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            final byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
