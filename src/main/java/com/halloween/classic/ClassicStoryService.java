package com.halloween.classic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassicStoryService {

    private final ClassicCatalogProvider catalogProvider;
    private final WikisourceClient wikisourceClient;
    private final WikisourceHtmlCleaner htmlCleaner;
    private final String baseUrl;
    private final int minBodyLength;

    public ClassicStoryService(ClassicCatalogProvider catalogProvider,
                               WikisourceClient wikisourceClient,
                               WikisourceHtmlCleaner htmlCleaner,
                               @Value("${wikisource.api.base-url}") String baseUrl,
                               @Value("${classic.min-body-length:1000}") int minBodyLength) {
        this.catalogProvider = catalogProvider;
        this.wikisourceClient = wikisourceClient;
        this.htmlCleaner = htmlCleaner;
        this.baseUrl = baseUrl;
        this.minBodyLength = minBodyLength;
    }

    public List<ClassicStoryDTO> getAll() {
        return catalogProvider.getAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "classicStories", key = "#slug")
    public ClassicStoryDTO getStory(String slug) {
        ClassicStoryDTO entry = catalogProvider.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clásico no encontrado"));

        String html = wikisourceClient.fetchPageHtml(entry.getSlug());
        String body = htmlCleaner.clean(html);
        if (body == null || body.strip().length() < minBodyLength) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "La página de Wikisource no contiene el texto de la historia");
        }

        ClassicStoryDTO dto = toSummary(entry);
        dto.setBody(body);
        return dto;
    }

    private ClassicStoryDTO toSummary(ClassicStoryDTO entry) {
        return new ClassicStoryDTO(
                entry.getSlug(),
                entry.getTitle(),
                entry.getAuthor(),
                entry.getTranslator(),
                entry.getYear(),
                entry.getLicense(),
                entry.getLicenseUrl(),
                entry.getAttribution(),
                buildSourceUrl(entry.getSlug()),
                null
        );
    }

    private String buildSourceUrl(String slug) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/wiki/{title}")
                .buildAndExpand(slug.replace(' ', '_'))
                .toUriString();
    }
}
