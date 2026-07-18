package com.aidatachat.adapters.out.mime;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidatachat.document.DocumentFixtures;
import org.junit.jupiter.api.Test;

class TikaDocumentMimeDetectionAdapterTest {

    private final TikaDocumentMimeDetectionAdapter adapter = new TikaDocumentMimeDetectionAdapter();

    @Test
    void detectsPdfByMagicBytes() {
        assertThat(adapter.detect(DocumentFixtures.pdfBytes(), "informe.pdf"))
                .isEqualTo("application/pdf");
    }

    @Test
    void detectsDocxAsOoxmlWordprocessingDocument() {
        assertThat(adapter.detect(DocumentFixtures.docxBytes(), "informe.docx"))
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument"
                                + ".wordprocessingml.document");
    }

    @Test
    void detectsPlainTextContent() {
        assertThat(adapter.detect(DocumentFixtures.plainTextBytes("hola mundo"), "notas.txt"))
                .isEqualTo("text/plain");
    }

    @Test
    void detectsJsonContent() {
        assertThat(
                        adapter.detect(
                                DocumentFixtures.plainTextBytes("{\"clave\":\"valor\"}"),
                                "datos.json"))
                .isIn("application/json", "text/plain");
    }

    @Test
    void detectsAnExecutableAsSomethingOtherThanTextOrPdf() {
        assertThat(adapter.detect(DocumentFixtures.executableBytes(), "informe.pdf"))
                .isNotIn("application/pdf", "text/plain");
    }
}
