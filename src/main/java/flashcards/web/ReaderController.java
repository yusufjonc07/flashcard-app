package flashcards.web;

import flashcards.model.DocumentSet;
import flashcards.model.WordPosition;
import flashcards.service.DocumentCache;
import flashcards.service.DocumentReader;
import flashcards.service.FlashcardService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * The document reader: shows one page of a document with its Korean words. Translations
 * and saving words go through {@link ReaderApiController}, so the page appears immediately
 * while the (slow) translation runs in the background.
 */
@Controller
public class ReaderController {

    /** Pages are rendered at 120 DPI; PDF positions are in points (72 per inch). */
    private static final double PIXELS_PER_POINT = 120.0 / 72.0;

    /** A clickable word box on the page image, in percent of the image size. */
    public record WordBox(String word, String left, String top, String width, String height) {
    }

    private final FlashcardService flashcards;
    private final DocumentCache documents;

    public ReaderController(FlashcardService flashcards, DocumentCache documents) {
        this.flashcards = flashcards;
        this.documents = documents;
    }

    @GetMapping("/sets/{id}/read")
    public String read(@PathVariable long id, @RequestParam(defaultValue = "1") int page, Model model)
            throws Exception {
        DocumentSet set = flashcards.set(id);
        List<DocumentReader.Page> pages = documents.pages(set);
        DocumentReader.Page current = documents.page(set, page);

        model.addAttribute("set", set);
        model.addAttribute("page", current);
        model.addAttribute("pageCount", pages.size());
        model.addAttribute("pageNumbers", pages.stream().map(DocumentReader.Page::number).toList());
        model.addAttribute("words", current.words());
        model.addAttribute("boxes", boxes(current));
        return "reader";
    }

    @GetMapping("/sets/{id}/pages/{number}.png")
    public ResponseEntity<byte[]> pageImage(@PathVariable long id, @PathVariable int number) throws Exception {
        byte[] png = documents.pageImage(flashcards.set(id), number);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(png);
    }

    /** Word boxes for words that are in the sidebar list (only PDFs have positions). */
    private static List<WordBox> boxes(DocumentReader.Page page) {
        if (page.image() == null) {
            return List.of();
        }
        double width = page.image().getWidth();
        double height = page.image().getHeight();
        return page.positions().stream()
                .map(position -> toBox(position, page.words(), width, height))
                .filter(box -> box != null)
                .toList();
    }

    private static WordBox toBox(WordPosition position, List<String> words, double width, double height) {
        // A PDF "word" can include particles (학교에서); link it to the longest listed word inside it.
        String match = words.stream()
                .filter(word -> position.getWord().contains(word))
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
        if (match == null) {
            return null;
        }
        double top = (position.getY() - position.getHeight()) * PIXELS_PER_POINT;
        return new WordBox(match,
                percent(position.getX() * PIXELS_PER_POINT, width),
                percent(top, height),
                percent(Math.max(4, position.getWidth() * PIXELS_PER_POINT), width),
                percent(Math.max(4, position.getHeight() * PIXELS_PER_POINT * 1.3), height));
    }

    private static String percent(double value, double total) {
        return String.format(Locale.ROOT, "%.3f%%", 100 * value / total);
    }
}
