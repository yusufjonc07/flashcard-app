package flashcards;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PDFExtractor implements TextExtractor {

    private final File filePath;

    public PDFExtractor(String pathname) {
        if (pathname == null || pathname.isBlank()) {
            throw new IllegalArgumentException("PDF path must not be blank");
        }

        filePath = new File(pathname);
    }

    @Override
    public String extract() throws IOException {
        if (!filePath.isFile()) {
            throw new FileNotFoundException("PDF file does not exist: " + filePath);
        }

        try (PDDocument document = Loader.loadPDF(filePath)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
