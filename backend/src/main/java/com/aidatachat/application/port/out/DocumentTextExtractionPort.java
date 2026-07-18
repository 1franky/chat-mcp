package com.aidatachat.application.port.out;

import java.util.List;

public interface DocumentTextExtractionPort {

    /**
     * {@code extension} is the lowercase file extension already validated against {@code
     * mimeType} by the upload pipeline (see {@code DocumentManagementService}'s MIME allowlist) —
     * it drives format dispatch, since a MIME of {@code text/plain} is ambiguous between
     * txt/md/csv/json (several of those formats have no reliable magic bytes and are allowed to
     * fall back to {@code text/plain}).
     */
    ExtractedDocument extract(String extension, String mimeType, byte[] content);

    record ExtractedDocument(List<ExtractedPage> pages) {

        public ExtractedDocument {
            pages = List.copyOf(pages);
        }
    }

    /**
     * A logical unit of extracted text: one PDF page (1-indexed {@code pageNumber}), or one
     * heading-delimited section for DOCX/Markdown ({@code sectionLabel}). Formats without a page or
     * section concept (TXT/CSV/JSON) yield a single page with both fields {@code null}.
     */
    record ExtractedPage(Integer pageNumber, String sectionLabel, String text) {}
}
