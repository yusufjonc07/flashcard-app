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
    protected void writeString(
            String text,
            List<TextPosition> positions) {

        StringBuilder currentWord = new StringBuilder();

        float startX = 0;
        float startY = 0;
        float endX = 0;
        float maxHeight = 0;

        for (TextPosition position : positions) {

            String character = position.getUnicode();

            if (character == null || character.isBlank()) {

                saveWord(currentWord, startX, startY,
                        endX, maxHeight);

                currentWord.setLength(0);
                continue;
            }

            float x = position.getXDirAdj();
            float y = position.getYDirAdj();
            float width = position.getWidthDirAdj();
            float height = position.getHeightDir();

            if (currentWord.isEmpty()) {
                startX = x;
                startY = y;
                maxHeight = height;
            }

            currentWord.append(character);

            endX = x + width;
            maxHeight = Math.max(maxHeight, height);
        }

        saveWord(currentWord, startX, startY,
                endX, maxHeight);
    }

    private void saveWord(
            StringBuilder word,
            float x,
            float y,
            float endX,
            float height) {

        if (!word.isEmpty()) {

            words.add(new WordPosition(
                    word.toString(),
                    x,
                    y,
                    endX - x,
                    height,
                    getCurrentPageNo()
            ));
        }
    }
}