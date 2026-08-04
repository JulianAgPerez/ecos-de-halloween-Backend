package com.halloween.classic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassicStoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WikisourceClient wikisourceClient;

    private static final String SAMPLE_HTML = """
            <div class="mw-content-ltr mw-parser-output" lang="es" dir="ltr"><div class="prp-pages-output">
            <div class="noprint ws-noexport" id="headertemplate"><a href="/wiki/El_beso">El Beso</a> Descargar como</div>
            <div class="ws-div" style="text-align:center;"><b>EL MONTE DE LAS ÁNIMAS</b></div>
            <p>La noche de difuntos me despertó a no sé qué hora.</p>
            <p>Intenté dormir de nuevo.</p>
            </div></div>
            """;

    @BeforeEach
    void setUp() {
        when(wikisourceClient.fetchPageHtml(anyString())).thenReturn(SAMPLE_HTML);
    }

    @Test
    void getAll_publicWithoutToken_returnsCatalog() throws Exception {
        mockMvc.perform(get("/api/classics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].license").exists())
                .andExpect(jsonPath("$[0].sourceUrl").exists())
                .andExpect(jsonPath("$[0].body").doesNotExist());
    }

    @Test
    void getBySlug_publicWithoutToken_returnsBody() throws Exception {
        mockMvc.perform(get("/api/classics/{slug}", "El monte de las ánimas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("El monte de las ánimas"))
                .andExpect(jsonPath("$.body").value(containsString("noche de difuntos")));
    }

    @Test
    void getBySlug_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/classics/{slug}", "no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("no encontrado")));
    }

    @Test
    void writeEndpoints_areNotPublic() throws Exception {
        mockMvc.perform(post("/api/classics"))
                .andExpect(status().isUnauthorized());
    }
}
