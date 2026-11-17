package flashcards.service;

import flashcards.model.DocumentSet;
import flashcards.repository.NotFoundException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the last few parsed documents in memory. Reading a PDF, rendering its pages and
 * running OCR is slow, so the reader should do it once per document, not once per page view.
 */
@Service
public class DocumentCache {

    private static final int MAX_DOCUMENTS = 4;

    private final Map<String, List<DocumentReader.Page>> pages =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<DocumentReader.Page>> eldest) {
                    return size() > MAX_DOCUMENTS;
                }
            };

    public synchronized List<DocumentReader.Page> pages(DocumentSet set) throws Exception {
        List<DocumentReader.Page> cached = pages.get(set.path());
        if (cached != null) {
            return cached;
        }
        Path path = Path.of(set.path());
        if (!set.isReadable() || !Files.isRegularFile(path)) {
            throw new NotFoundException("The source document of \"" + set.name()
                    + "\" is no longer available. Upload it again to read it.");
        }
        List<DocumentReader.Page> read = DocumentReader.read(path);
        if (read.isEmpty()) {
            throw new IOException(set.name() + " has no pages");
        }
        pages.put(set.path(), read);
        return read;
    }

    /** The page with this 1-based number, clamped to the document's range. */
    public DocumentReader.Page page(DocumentSet set, int number) throws Exception {
        List<DocumentReader.Page> all = pages(set);
        return all.get(Math.max(1, Math.min(number, all.size())) - 1);
    }

    /** PNG image of a page, or {@code null} if the page is text only. */
    public byte[] pageImage(DocumentSet set, int number) throws Exception {
        DocumentReader.Page page = page(set, number);
        if (page.image() == null) {
            return null;
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(page.image(), "png", png);
        return png.toByteArray();
    }
}
