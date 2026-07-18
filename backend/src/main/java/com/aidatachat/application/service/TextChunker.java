package com.aidatachat.application.service;

import com.aidatachat.application.port.out.DocumentTextExtractionPort.ExtractedPage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure text chunker, no Spring/framework dependencies: normalizes, splits into paragraphs, merges
 * small consecutive paragraphs, and sub-chunks (sliding window + overlap, snapped to the nearest
 * whitespace) only the paragraphs/groups that still exceed {@code chunkSizeChars}. Operates on
 * each {@link ExtractedPage} independently — a chunk never crosses a page/section boundary, so it
 * always keeps a single, correct {@code pageNumber}/{@code sectionLabel} for future citations.
 */
public final class TextChunker {

    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final Pattern TRIPLE_NEWLINE = Pattern.compile("\n{3,}");
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\n{2,}");
    private static final Pattern TRAILING_LINE_WHITESPACE = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern LEADING_LINE_WHITESPACE = Pattern.compile("(?m)^[ \\t]+");

    private TextChunker() {}

    public record Chunk(int chunkIndex, String content, Integer pageNumber, String sectionLabel) {}

    public static List<Chunk> chunk(
            List<ExtractedPage> pages, int chunkSizeChars, int overlapChars) {
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (ExtractedPage page : pages) {
            String normalized = normalize(page.text());
            if (normalized.isBlank()) {
                continue;
            }
            for (String unit : mergeSmallParagraphs(splitParagraphs(normalized), chunkSizeChars)) {
                for (String piece : subChunk(unit, chunkSizeChars, overlapChars)) {
                    if (piece.isBlank()) {
                        continue;
                    }
                    chunks.add(new Chunk(index++, piece, page.pageNumber(), page.sectionLabel()));
                }
            }
        }
        return chunks;
    }

    /**
     * Preserves the distinction between a single and a double newline: a double newline marks a
     * paragraph boundary that chunking relies on, so it is never collapsed into a single one —
     * only runs of 3+ newlines are capped down to exactly 2.
     */
    public static String normalize(String text) {
        String unixNewlines = text.replace("\r\n", "\n").replace("\r", "\n");
        String noControlChars = CONTROL_CHARS.matcher(unixNewlines).replaceAll("");
        String noLineEdgeWhitespace =
                LEADING_LINE_WHITESPACE
                        .matcher(TRAILING_LINE_WHITESPACE.matcher(noControlChars).replaceAll(""))
                        .replaceAll("");
        String collapsedHorizontal =
                HORIZONTAL_WHITESPACE.matcher(noLineEdgeWhitespace).replaceAll(" ");
        return TRIPLE_NEWLINE.matcher(collapsedHorizontal).replaceAll("\n\n").strip();
    }

    public static List<String> splitParagraphs(String normalized) {
        return Arrays.stream(PARAGRAPH_BOUNDARY.split(normalized))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static List<String> mergeSmallParagraphs(List<String> paragraphs, int chunkSizeChars) {
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (buffer.isEmpty()) {
                buffer.append(paragraph);
            } else if (buffer.length() + 2 + paragraph.length() <= chunkSizeChars) {
                buffer.append("\n\n").append(paragraph);
            } else {
                merged.add(buffer.toString());
                buffer = new StringBuilder(paragraph);
            }
        }
        if (!buffer.isEmpty()) {
            merged.add(buffer.toString());
        }
        return merged;
    }

    public static List<String> subChunk(String text, int chunkSizeChars, int overlapChars) {
        String trimmed = text.strip();
        if (trimmed.length() <= chunkSizeChars) {
            return List.of(trimmed);
        }
        List<String> pieces = new ArrayList<>();
        int start = 0;
        int length = trimmed.length();
        while (start < length) {
            int end = Math.min(start + chunkSizeChars, length);
            if (end < length) {
                int lastSpace = trimmed.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            String piece = trimmed.substring(start, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= length) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
        return pieces;
    }
}
