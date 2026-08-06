package com.halloween.classic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WikisourceHtmlCleanerTest {

    private final WikisourceHtmlCleaner cleaner = new WikisourceHtmlCleaner();

    @Test
    void clean_removesHeaderAndKeepsStory() {
        String html = """
                <div class="mw-content-ltr mw-parser-output"><div class="prp-pages-output">
                <style>.mw-parser-output #headertemplate{color:red}</style>
                <div class="noprint ws-noexport" id="headertemplate">
                <a href="/wiki/El_beso" title="El beso">El Beso</a> <b>Obras de Becquer</b>
                <img src="descargar.png" alt="Descargar como" /> Descargar como
                </div>
                <div id="ws-data" class="ws-noexport" style="display:none"><span class="ws-year">1885</span></div>
                <span title="El gato negro (de Verneuil tr.) - español" class="interwiki-extra" style="display:none;" lang="es"><a href="/wiki/El_gato_negro_(de_Verneuil_tr.)">El gato negro (de Verneuil tr.)</a></span>
                <span title="Tales (Poe)/The Black Cat - inglés" class="interwiki-extra" style="display:none;" lang="en"><a href="https://en.wikisource.org/wiki/Tales_(Poe)/The_Black_Cat">English</a></span>
                <p><br /></p>
                <div class="ws-div" style="text-align:center;"><b>EL MONTE DE LAS ÁNIMAS</b></div>
                <p><span class="dropinitial"><span typeof="mw:File"><img alt="L" src="letra.png" /></span></span><span style="font-variant: small-caps;">a</span> noche de difuntos me despertó.</p>
                <p>Intenté dormir de nuevo.</p>
                <div class="np"></div>
                <p>Sea de ello lo que quiera.</p>
                </div></div>
                """;

        String result = cleaner.clean(html);

        assertThat(result).contains("EL MONTE DE LAS ÁNIMAS");
        assertThat(result).contains("La noche de difuntos me despertó.");
        assertThat(result).contains("Intenté dormir de nuevo.");
        assertThat(result).contains("Sea de ello lo que quiera.");
        assertThat(result).doesNotContain("Descargar como");
        assertThat(result).doesNotContain("El Beso");
        assertThat(result).doesNotContain("Obras de Becquer");
        assertThat(result).doesNotContain("1885");
        assertThat(result).doesNotContain("Verneuil");
        assertThat(result).doesNotContain("English");
    }

    @Test
    void clean_returnsEmptyForContentlessHtml() {
        String result = cleaner.clean("<html><body><p>   </p></body></html>");
        assertThat(result).isEmpty();
    }
}
