package com.aidatachat.adapters.out.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.DocumentProcessingException;
import com.aidatachat.application.port.out.DocumentTextExtractionPort.ExtractedDocument;
import com.aidatachat.application.port.out.DocumentTextExtractionPort.ExtractedPage;
import com.aidatachat.document.DocumentFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorAdapterTest {

    private final DocumentTextExtractorAdapter adapter =
            new DocumentTextExtractorAdapter(200, 2_000_000);

    @Test
    void extractsOnePagePerPdfPageWithOneIndexedPageNumbers() {
        byte[] content = DocumentFixtures.pdfBytesWithPages(List.of("Pagina uno", "Pagina dos"));

        ExtractedDocument extracted = adapter.extract("pdf", "application/pdf", content);

        assertThat(extracted.pages()).hasSize(2);
        assertThat(extracted.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(extracted.pages().get(0).text()).contains("Pagina uno");
        assertThat(extracted.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(extracted.pages().get(1).text()).contains("Pagina dos");
    }

    @Test
    void abortsPdfExtractionWhenPageCountExceedsTheConfiguredMaximum() {
        DocumentTextExtractorAdapter limited = new DocumentTextExtractorAdapter(1, 2_000_000);
        byte[] content = DocumentFixtures.pdfBytesWithPages(List.of("uno", "dos"));

        assertThatThrownBy(() -> limited.extract("pdf", "application/pdf", content))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(
                        e ->
                                assertThat(((DocumentProcessingException) e).reasonCode())
                                        .isEqualTo("too_many_pages"));
    }

    @Test
    void extractsDocxTextAsASinglePage() {
        byte[] content = DocumentFixtures.docxBytesWithText("Contenido del documento Word");

        ExtractedDocument extracted =
                adapter.extract(
                        "docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        content);

        assertThat(extracted.pages()).hasSize(1);
        assertThat(extracted.pages().getFirst().text()).contains("Contenido del documento Word");
        assertThat(extracted.pages().getFirst().pageNumber()).isNull();
    }

    @Test
    void neverResolvesAnExternalEntityInAMaliciousDocx() {
        byte[] content = DocumentFixtures.docxXxeBytes();

        try {
            ExtractedDocument extracted =
                    adapter.extract(
                            "docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            content);
            for (ExtractedPage page : extracted.pages()) {
                assertThat(page.text()).doesNotContain("root:");
            }
        } catch (DocumentProcessingException expected) {
            // Rejecting the malformed/DOCTYPE-bearing XML outright is an equally acceptable,
            // and in fact the actually observed, safe outcome.
        }
    }

    @Test
    void splitsMarkdownIntoSectionsByHeading() {
        byte[] content =
                DocumentFixtures.plainTextBytes(
                        "Texto antes de cualquier encabezado.\n\n"
                                + "# Uno\n"
                                + "contenido de la primera seccion\n\n"
                                + "## Dos\n"
                                + "contenido de la segunda seccion\n");

        ExtractedDocument extracted = adapter.extract("md", "text/markdown", content);

        assertThat(extracted.pages()).hasSize(3);
        assertThat(extracted.pages().get(0).sectionLabel()).isNull();
        assertThat(extracted.pages().get(1).sectionLabel()).isEqualTo("Uno");
        assertThat(extracted.pages().get(1).text()).contains("primera seccion");
        assertThat(extracted.pages().get(2).sectionLabel()).isEqualTo("Dos");
        assertThat(extracted.pages().get(2).text()).contains("segunda seccion");
    }

    @Test
    void extractsPlainTextCsvAndJsonAsASinglePageEach() {
        for (String extension : List.of("txt", "csv", "json")) {
            ExtractedDocument extracted =
                    adapter.extract(
                            extension, "text/plain", DocumentFixtures.plainTextBytes("hola"));
            assertThat(extracted.pages()).hasSize(1);
            assertThat(extracted.pages().getFirst().text()).isEqualTo("hola");
        }
    }

    @Test
    void rejectsContentThatIsNotValidUtf8() {
        byte[] malformed = new byte[] {(byte) 0xFF, (byte) 0xFE, 'h', 'i'};

        assertThatThrownBy(() -> adapter.extract("txt", "text/plain", malformed))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(
                        e ->
                                assertThat(((DocumentProcessingException) e).reasonCode())
                                        .isEqualTo("unsupported_text_encoding"));
    }

    @Test
    void abortsWhenExtractedTextExceedsTheConfiguredCharacterLimit() {
        DocumentTextExtractorAdapter tight = new DocumentTextExtractorAdapter(200, 5);
        byte[] content = "texto mas largo que el limite".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> tight.extract("txt", "text/plain", content))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(
                        e ->
                                assertThat(((DocumentProcessingException) e).reasonCode())
                                        .isEqualTo("content_too_large"));
    }

    @Test
    void rejectsAnUnsupportedExtension() {
        assertThatThrownBy(
                        () ->
                                adapter.extract(
                                        "exe", "application/octet-stream", new byte[] {1, 2, 3}))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(
                        e ->
                                assertThat(((DocumentProcessingException) e).reasonCode())
                                        .isEqualTo("unsupported_content"));
    }
}
