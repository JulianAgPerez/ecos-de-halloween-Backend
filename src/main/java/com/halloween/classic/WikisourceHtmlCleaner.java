package com.halloween.classic;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WikisourceHtmlCleaner {

    public String clean(String html) {
        Document doc = Jsoup.parse(html);

        for (Element drop : doc.select(".dropinitial")) {
            Element img = drop.selectFirst("img");
            String alt = img != null ? img.attr("alt") : "";
            Element parent = drop.parent();
            drop.remove();
            if (parent != null && !alt.isBlank()) {
                parent.prependText(alt);
            }
        }

        doc.select("style, script").remove();
        doc.select("#headertemplate, #ws-data, #conv-idiomas, figure, img, sup, table").remove();
        doc.select(".noprint, .noexcerpt, .toc, .navbox, .infobox, .metadata, .thumb, .hatnote, .portal, .mw-editsection, .mw-empty-elt, .pagenum, .np, .references, .ws-noexport, .interwiki-extra").remove();

        Element container = doc.selectFirst(".prp-pages-output");
        if (container == null) {
            container = doc.selectFirst(".mw-parser-output");
        }
        if (container == null) {
            return "";
        }

        List<String> paragraphs = new ArrayList<>();
        for (Element child : container.children()) {
            String text = child.text().replace('\u00A0', ' ').trim();
            if (!text.isEmpty()) {
                paragraphs.add(text);
            }
        }
        return String.join("\n\n", paragraphs);
    }
}
