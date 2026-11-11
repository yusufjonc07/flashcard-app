package flashcards;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

public class PPTExtractor implements TextExtractor {

    private final File filePath;

    public PPTExtractor(String pathname) {
        if (pathname == null || pathname.isBlank()) {
            throw new IllegalArgumentException("PowerPoint path must not be blank");
        }

        filePath = new File(pathname);
    }

    @Override
    public String extract() throws IOException {
        if (!filePath.isFile()) {
            throw new FileNotFoundException("PowerPoint file does not exist: " + filePath);
        }

        try (POITextExtractor extractor = ExtractorFactory.createExtractor(filePath)) {
            return extractor.getText();
        }
    }
}
