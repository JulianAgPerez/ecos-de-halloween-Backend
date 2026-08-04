package com.halloween.classic;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ClassicCatalogProvider {

    public static final String LICENSE_PUBLIC_DOMAIN = "dominio-publico";
    public static final String LICENSE_CC_BY_SA = "cc-by-sa-4.0";
    private static final String CC_BY_SA_URL = "https://creativecommons.org/licenses/by-sa/4.0/deed.es";
    private static final String WIKISOURCE_ATTRIBUTION = "Traducción original de Wikisource";

    private static final List<ClassicStoryDTO> CATALOG = List.of(
            // ---- Gustavo Adolfo Bécquer (dominio público) ----
            entry("El monte de las ánimas", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El rayo de luna", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("Los ojos verdes", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("Maese Pérez el organista", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("La cruz del Diablo", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("La ajorca de oro", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El miserere (Bécquer)", "El miserere", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El Cristo de la calavera", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El gnomo", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("La cueva de la mora", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("La rosa de pasión", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("La corza blanca", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El beso", "Gustavo Adolfo Bécquer", null, null, LICENSE_PUBLIC_DOMAIN),

            // ---- Horacio Quiroga (dominio público) ----
            entry("El almohadón de pluma", "Horacio Quiroga", null, 1918, LICENSE_PUBLIC_DOMAIN),
            entry("La gallina degollada", "Horacio Quiroga", null, 1918, LICENSE_PUBLIC_DOMAIN),
            entry("El hombre muerto", "Horacio Quiroga", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El vampiro (Quiroga)", "El vampiro", "Horacio Quiroga", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("El espectro (Quiroga)", "El espectro", "Horacio Quiroga", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("A la deriva", "Horacio Quiroga", null, null, LICENSE_PUBLIC_DOMAIN),
            entry("Los buques suicidantes", "Horacio Quiroga", null, null, LICENSE_PUBLIC_DOMAIN),

            // ---- Edgar Allan Poe (traducciones de dominio público) ----
            entry("El gato negro (Cano y Cueto tr.)", "El gato negro", "Edgar Allan Poe", "Manuel Cano y Cueto", 1871, LICENSE_PUBLIC_DOMAIN),
            entry("El barril de amontillado (Cano y Cueto tr.)", "El barril de amontillado", "Edgar Allan Poe", "Manuel Cano y Cueto", 1871, LICENSE_PUBLIC_DOMAIN),
            entry("El escarabajo de oro (Cano y Cueto tr.)", "El escarabajo de oro", "Edgar Allan Poe", "Manuel Cano y Cueto", 1871, LICENSE_PUBLIC_DOMAIN),
            entry("El retrato oval (Cano y Cueto tr.)", "El retrato oval", "Edgar Allan Poe", "Manuel Cano y Cueto", 1871, LICENSE_PUBLIC_DOMAIN),
            entry("El entierro prematuro", "Edgar Allan Poe", "Manuel Cano y Cueto", 1871, LICENSE_PUBLIC_DOMAIN),
            entry("El pozo y el péndulo (Olivera tr.)", "El pozo y el péndulo", "Edgar Allan Poe", "Carlos Olivera", 1884, LICENSE_PUBLIC_DOMAIN),
            entry("La carta robada (Olivera tr.)", "La carta robada", "Edgar Allan Poe", "Carlos Olivera", 1884, LICENSE_PUBLIC_DOMAIN),
            entry("La caída de la casa Usher", "Edgar Allan Poe", "Carmen Torres Calderón de Pinillos", 1919, LICENSE_PUBLIC_DOMAIN),
            entry("El crimen de la Rue Morgue", "Edgar Allan Poe", "Carmen Torres Calderón de Pinillos", 1919, LICENSE_PUBLIC_DOMAIN),
            entry("La máscara de la muerte", "Edgar Allan Poe", "Carmen Torres Calderón de Pinillos", 1919, LICENSE_PUBLIC_DOMAIN),

            // ---- H. P. Lovecraft (traducciones originales de Wikisource, CC-BY-SA 4.0) ----
            entry("El templo (H. P. Lovecraft)", "El templo", "H. P. Lovecraft", null, 1925, LICENSE_CC_BY_SA, CC_BY_SA_URL, WIKISOURCE_ATTRIBUTION),
            entry("El alquimista (Howard Phillips Lovecraft)", "El alquimista", "H. P. Lovecraft", null, 1908, LICENSE_CC_BY_SA, CC_BY_SA_URL, WIKISOURCE_ATTRIBUTION),
            entry("El libro (Howard Phillips Lovecraft)", "El libro", "H. P. Lovecraft", null, 1938, LICENSE_CC_BY_SA, CC_BY_SA_URL, WIKISOURCE_ATTRIBUTION)
    );

    public List<ClassicStoryDTO> getAll() {
        return CATALOG;
    }

    public Optional<ClassicStoryDTO> findBySlug(String slug) {
        return CATALOG.stream()
                .filter(story -> story.getSlug().equalsIgnoreCase(slug))
                .findFirst();
    }

    private static ClassicStoryDTO entry(String slug, String author, String translator, Integer year, String license) {
        return entry(slug, slug, author, translator, year, license, null, null);
    }

    private static ClassicStoryDTO entry(String slug, String title, String author, String translator, Integer year, String license) {
        return entry(slug, title, author, translator, year, license, null, null);
    }

    private static ClassicStoryDTO entry(String slug, String title, String author, String translator, Integer year, String license, String licenseUrl, String attribution) {
        return new ClassicStoryDTO(slug, title, author, translator, year, license, licenseUrl, attribution, null, null);
    }
}
