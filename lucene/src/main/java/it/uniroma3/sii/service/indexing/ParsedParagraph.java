package it.uniroma3.sii.service.indexing;

public record ParsedParagraph(String text, int pageNumber, int paragraphIndex, int startOffset, int endOffset) {
}
