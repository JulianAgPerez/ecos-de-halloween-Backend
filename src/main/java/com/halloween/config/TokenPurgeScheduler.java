package com.halloween.config;

import com.halloween.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TokenPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenPurgeScheduler.class);

    private final TokenRepository tokenRepository;
    private final int retentionDays;

    public TokenPurgeScheduler(TokenRepository tokenRepository,
                               @Value("${app.tokens.purge-retention-days:30}") int retentionDays) {
        this.tokenRepository = tokenRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.tokens.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredAndRevokedTokens() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = tokenRepository.deleteExpiredOrRevokedBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired or revoked tokens created before {}", deleted, cutoff);
        }
    }
}
