package flashcards.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import flashcards.model.WordPosition;

/**
 * Collects every piece of text on each PDF page with its position. PDFBox hands over the text
 * split at spaces; for each piece we also keep the left and right edge of every character, so
 * the reader can highlight single words inside a piece that has no spaces.
 */
public class PDFExtractor extends PDFTextStripper {

    private final List<WordPosition> words = new ArrayList<>();

    public PDFExtractor() throws IOException {
        super();
        setSortByPosition(true);
    }

    public List<WordPosition> extract(File file) throws IOException {
        words.clear();
        try (PDDocument document = Loader.loadPDF(file)) {
            getText(document);
        }
        return new ArrayList<>(words);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        Piece piece = new Piece();
        for (TextPosition position : positions) {
            String characters = position.getUnicode();
            if (characters == null || characters.isBlank()) {
                save(piece);
                piece = new Piece();
                continue;
            }
            piece.add(characters, position);
        }
        save(piece);
    }

    private void save(Piece piece) {
        if (piece.text.isEmpty()) {
            return;
        }
        int count = piece.lefts.size();
        float[] lefts = new float[count];
        float[] rights = new float[count];
        for (int i = 0; i < count; i++) {
            lefts[i] = piece.lefts.get(i);
            rights[i] = piece.rights.get(i);
        }
        words.add(new WordPosition(piece.text.toString(), piece.x, piece.y, piece.right - piece.x,
                piece.height, getCurrentPageNo(), lefts, rights));
    }

    /** The text between two spaces, built up one character at a time. */
    private static final class Piece {
        final StringBuilder text = new StringBuilder();
        final List<Float> lefts = new ArrayList<>();
        final List<Float> rights = new ArrayList<>();
        float x;
        float y;
        float right;
        float height;

        void add(String characters, TextPosition position) {
            float left = position.getXDirAdj();
            float width = position.getWidthDirAdj();
            if (text.isEmpty()) {
                x = left;
                y = position.getYDirAdj();
            }
            text.append(characters);
            // A glyph can stand for more than one character (ligatures); split its width evenly.
            int n = characters.length();
            for (int i = 0; i < n; i++) {
                lefts.add(left + width * i / n);
                rights.add(left + width * (i + 1) / n);
            }
            right = left + width;
            height = Math.max(height, position.getHeightDir());
        }
    }
}
