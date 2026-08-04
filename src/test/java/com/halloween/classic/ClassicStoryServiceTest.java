package com.halloween.classic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassicStoryServiceTest {

    @Mock private ClassicCatalogProvider catalogProvider;
    @Mock private WikisourceClient wikisourceClient;
    @Mock private WikisourceHtmlCleaner htmlCleaner;

    private ClassicStoryService service() {
        return new ClassicStoryService(catalogProvider, wikisourceClient, htmlCleaner, "https://es.wikisource.org");
    }

    private ClassicStoryDTO entry() {
        return new ClassicStoryDTO("El gato negro (Cano y Cueto tr.)", "El gato negro",
                "Edgar Allan Poe", "Manuel Cano y Cueto", 1871,
                ClassicCatalogProvider.LICENSE_PUBLIC_DOMAIN, null, null, null, null);
    }

    @Test
    void getAll_returnsSummariesWithoutBody() {
        when(catalogProvider.getAll()).thenReturn(List.of(entry()));

        List<ClassicStoryDTO> result = service().getAll();

        assertThat(result).hasSize(1);
        ClassicStoryDTO dto = result.get(0);
        assertThat(dto.getTitle()).isEqualTo("El gato negro");
        assertThat(dto.getBody()).isNull();
        assertThat(dto.getSourceUrl()).isEqualTo("https://es.wikisource.org/wiki/El_gato_negro_(Cano_y_Cueto_tr.)");
    }

    @Test
    void getStory_returnsDtoWithBody() {
        when(catalogProvider.findBySlug("El gato negro (Cano y Cueto tr.)")).thenReturn(Optional.of(entry()));
        when(wikisourceClient.fetchPageHtml("El gato negro (Cano y Cueto tr.)")).thenReturn("<p>Era un gato.</p>");
        when(htmlCleaner.clean("<p>Era un gato.</p>")).thenReturn("Era un gato.");

        ClassicStoryDTO dto = service().getStory("El gato negro (Cano y Cueto tr.)");

        assertThat(dto.getBody()).isEqualTo("Era un gato.");
        assertThat(dto.getLicense()).isEqualTo(ClassicCatalogProvider.LICENSE_PUBLIC_DOMAIN);
    }

    @Test
    void getStory_unknownSlug_throws404() {
        when(catalogProvider.findBySlug("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getStory("no-existe"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getStory_ccBySa_includesLicenseAndAttribution() {
        ClassicStoryDTO lovecraft = new ClassicStoryDTO("El templo (H. P. Lovecraft)", "El templo",
                "H. P. Lovecraft", null, 1925,
                ClassicCatalogProvider.LICENSE_CC_BY_SA, "https://creativecommons.org/licenses/by-sa/4.0/deed.es",
                "Traducción original de Wikisource", null, null);
        when(catalogProvider.findBySlug("El templo (H. P. Lovecraft)")).thenReturn(Optional.of(lovecraft));
        when(wikisourceClient.fetchPageHtml("El templo (H. P. Lovecraft)")).thenReturn("<p>Texto.</p>");
        when(htmlCleaner.clean("<p>Texto.</p>")).thenReturn("Texto.");

        ClassicStoryDTO dto = service().getStory("El templo (H. P. Lovecraft)");

        assertThat(dto.getLicense()).isEqualTo(ClassicCatalogProvider.LICENSE_CC_BY_SA);
        assertThat(dto.getLicenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/deed.es");
        assertThat(dto.getAttribution()).isEqualTo("Traducción original de Wikisource");
    }
}
