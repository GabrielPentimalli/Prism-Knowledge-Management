package it.uniroma3.sii.service.indexing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.model.DocumentPage;
import it.uniroma3.sii.model.DocumentType;

@Service
public class DocumentExtractionService {

    public List<ParsedParagraph> extract(Path sourcePath, DocumentType type) {
        if (type == DocumentType.PDF) {
            return extractPdf(sourcePath);
        }
        return extractHtml(sourcePath);
    }

    /**
     * Estrae il testo completo pagina per pagina, senza filtrare paragrafi corti.
     * Per HTML restituisce una singola pagina con tutto il body.
     */
    public List<DocumentPage> extractPages(Path sourcePath, DocumentType type) {
        if (type == DocumentType.PDF) {
            return extractPdfPages(sourcePath);
        }
        return extractHtmlPages(sourcePath);
    }

    private List<DocumentPage> extractPdfPages(Path sourcePath) {
        List<DocumentPage> output = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(sourcePath.toFile())) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                output.add(new DocumentPage(page, text == null ? "" : text));
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Errore estrazione pagine PDF locale: " + sourcePath, e);
        }
    }

    private List<DocumentPage> extractHtmlPages(Path sourcePath) {
        try {
            Document html = Jsoup.parse(sourcePath.toFile(), "UTF-8");
            StringBuilder sb = new StringBuilder();
            for (Element node : html.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre")) {
                String text = node.text().trim();
                if (!text.isEmpty()) {
                    sb.append(text).append("\n\n");
                }
            }
            if (sb.length() == 0) {
                sb.append(html.body() != null ? html.body().text() : html.text());
            }
            return List.of(new DocumentPage(1, sb.toString().trim()));
        } catch (IOException e) {
            throw new IllegalStateException("Errore estrazione pagine HTML locale: " + sourcePath, e);
        }
    }

    private List<ParsedParagraph> extractPdf(Path sourcePath) {
        List<ParsedParagraph> output = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(sourcePath.toFile())) {
            AtomicInteger paragraphIndex = new AtomicInteger(0);
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                if (text == null || text.isBlank()) {
                    continue;
                }
                int runningOffset = 0;
                for (String paragraph : splitParagraphs(text)) {
                    String normalized = paragraph.trim();
                    if (normalized.length() < 30) {
                        runningOffset += paragraph.length();
                        continue;
                    }
                    output.add(new ParsedParagraph(
                            normalized,
                            page,
                            paragraphIndex.getAndIncrement(),
                            runningOffset,
                            runningOffset + normalized.length()));
                    runningOffset += paragraph.length();
                }
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Errore estrazione PDF locale: " + sourcePath, e);
        }
    }

    private List<ParsedParagraph> extractHtml(Path sourcePath) {
        List<ParsedParagraph> output = new ArrayList<>();
        try {
            Document html = Jsoup.parse(sourcePath.toFile(), "UTF-8");
            AtomicInteger paragraphIndex = new AtomicInteger(0);
            int runningOffset = 0;

            for (Element paragraph : html.select("p")) {
                String text = paragraph.text().trim();
                if (text.length() < 20) {
                    continue;
                }
                output.add(new ParsedParagraph(
                        text,
                        1,
                        paragraphIndex.getAndIncrement(),
                        runningOffset,
                        runningOffset + text.length()));
                runningOffset += text.length() + 1;
            }

            if (!output.isEmpty()) {
                return output;
            }

            String fallback = html.body() != null ? html.body().text() : html.text();
            runningOffset = 0;
            for (String paragraph : splitParagraphs(fallback)) {
                String normalized = paragraph.trim();
                if (normalized.length() < 20) {
                    runningOffset += paragraph.length();
                    continue;
                }
                output.add(new ParsedParagraph(
                        normalized,
                        1,
                        paragraphIndex.getAndIncrement(),
                        runningOffset,
                        runningOffset + normalized.length()));
                runningOffset += paragraph.length();
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Errore parsing HTML locale: " + sourcePath, e);
        }
    }

    private List<String> splitParagraphs(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return List.of(input.split("(\\r?\\n\\s*\\r?\\n)|(\\r?\\n)"));
    }
}
