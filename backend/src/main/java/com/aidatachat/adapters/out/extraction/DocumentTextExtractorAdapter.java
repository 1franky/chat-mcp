package com.aidatachat.adapters.out.extraction;

import com.aidatachat.application.exception.DocumentProcessingException;
import com.aidatachat.application.port.out.DocumentTextExtractionPort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Local, deterministic, network-free extraction — no fake needed (same precedent as Tika MIME
 * detection). PDFBox/POI stay confined to this adapter, never imported from application/domain.
 *
 * <p>Enforces {@code maxPages}/{@code maxCharacters} internally, as early as possible (before
 * doing further extraction work), rather than after the fact — a genuine resource-exhaustion
 * protection, not just bookkeeping.
 */
public final class DocumentTextExtractorAdapter implements DocumentTextExtractionPort {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final int MAX_SECTION_LABEL_LENGTH = 255;

    private final int maxPages;
    private final long maxCharacters;

    public DocumentTextExtractorAdapter(int maxPages, long maxCharacters) {
        this.maxPages = maxPages;
        this.maxCharacters = maxCharacters;
    }

    @Override
    public ExtractedDocument extract(String extension, String mimeType, byte[] content) {
        return switch (extension) {
            case "pdf" -> extractPdf(content);
            case "docx" -> extractDocx(content);
            case "md", "markdown" -> extractMarkdown(content);
            case "txt", "csv", "json" -> extractPlainText(content);
            default -> throw new DocumentProcessingException("unsupported_content");
        };
    }

    private ExtractedDocument extractPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount > maxPages) {
                throw new DocumentProcessingException("too_many_pages");
            }
            List<ExtractedPage> pages = new ArrayList<>(pageCount);
            PDFTextStripper stripper = new PDFTextStripper();
            long totalCharacters = 0;
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                totalCharacters += text.length();
                if (totalCharacters > maxCharacters) {
                    throw new DocumentProcessingException("content_too_large");
                }
                pages.add(new ExtractedPage(page, null, text));
            }
            return new ExtractedDocument(pages);
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DocumentProcessingException("extraction_failed", e);
        }
    }

    private ExtractedDocument extractDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            enforceMaxCharacters(text.length());
            return new ExtractedDocument(List.of(new ExtractedPage(null, null, text)));
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DocumentProcessingException("extraction_failed", e);
        }
    }

    private ExtractedDocument extractMarkdown(byte[] content) {
        String text = decodeStrictUtf8(content);
        enforceMaxCharacters(text.length());
        List<ExtractedPage> sections = new ArrayList<>();
        String currentLabel = null;
        StringBuilder currentText = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line.strip());
            if (matcher.matches()) {
                if (!currentText.isEmpty()) {
                    sections.add(new ExtractedPage(null, currentLabel, currentText.toString()));
                }
                String label = matcher.group(1).strip();
                currentLabel =
                        label.length() > MAX_SECTION_LABEL_LENGTH
                                ? label.substring(0, MAX_SECTION_LABEL_LENGTH)
                                : label;
                currentText = new StringBuilder();
            } else {
                currentText.append(line).append('\n');
            }
        }
        if (!currentText.isEmpty() || sections.isEmpty()) {
            sections.add(new ExtractedPage(null, currentLabel, currentText.toString()));
        }
        return new ExtractedDocument(sections);
    }

    private ExtractedDocument extractPlainText(byte[] content) {
        String text = decodeStrictUtf8(content);
        enforceMaxCharacters(text.length());
        return new ExtractedDocument(List.of(new ExtractedPage(null, null, text)));
    }

    private void enforceMaxCharacters(long totalCharacters) {
        if (totalCharacters > maxCharacters) {
            throw new DocumentProcessingException("content_too_large");
        }
    }

    private String decodeStrictUtf8(byte[] content) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new DocumentProcessingException("unsupported_text_encoding", e);
        }
    }
}
