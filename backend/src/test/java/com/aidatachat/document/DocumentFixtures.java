package com.aidatachat.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/** Byte fixtures for the document formats supported by the upload pipeline. */
public final class DocumentFixtures {

    private static final String CONTENT_TYPES_XML =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels"
                  ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml"
                  ContentType="application/vnd.openxmlformats-officedocument\
            .wordprocessingml.document.main+xml"/>
            </Types>
            """;

    private static final String RELS_XML =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1"
                  Type="http://schemas.openxmlformats.org/officeDocument/2006\
            /relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """;

    private static final String DOCUMENT_XML =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p/></w:body>
            </w:document>
            """;

    private DocumentFixtures() {}

    public static byte[] pdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF"
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] plainTextBytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] executableBytes() {
        // ELF magic number: real binaries are never text/plain nor application/pdf.
        return new byte[] {0x7f, 'E', 'L', 'F', 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    public static byte[] docxBytes() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML);
            writeEntry(zip, "_rels/.rels", RELS_XML);
            writeEntry(zip, "word/document.xml", DOCUMENT_XML);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    /**
     * A structurally valid docx (passes Tika's OOXML detection) whose {@code word/document.xml}
     * entry inflates to just over 100 MB of highly compressible zero bytes, to exercise the
     * zip-bomb guard without needing a genuinely huge upload.
     */
    public static byte[] docxZipBombBytes() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML);
            writeEntry(zip, "_rels/.rels", RELS_XML);
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 101; i++) {
                zip.write(chunk);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** A real, structurally valid single-page PDF with the given text, built via PDFBox itself. */
    public static byte[] pdfBytesWithText(String text) {
        return pdfBytesWithPages(List.of(text));
    }

    /** A real, structurally valid multi-page PDF, one line of text per page. */
    public static byte[] pdfBytesWithPages(List<String> pageTexts) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A real docx with a single paragraph of text, built via POI itself. */
    public static byte[] docxBytesWithText(String text) {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A structurally valid docx (passes Tika's OOXML detection) whose {@code word/document.xml}
     * declares a DOCTYPE with an external entity pointing at a local file, to verify the
     * extraction adapter never resolves it into the extracted text.
     */
    public static byte[] docxXxeBytes() {
        String maliciousDocumentXml =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body>
                </w:document>
                """;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML);
            writeEntry(zip, "_rels/.rels", RELS_XML);
            writeEntry(zip, "word/document.xml", maliciousDocumentXml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }
}
