package flashcards.web;

import flashcards.model.DocumentSet;
import flashcards.model.WordPosition;
import flashcards.service.DocumentCache;
import flashcards.service.DocumentReader;
import flashcards.service.FlashcardService;
import flashcards.service.KoreanWordExtractor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The document reader: shows a document one page at a time (two side by side for portrait
 * pages) with each page's Korean words. Translations
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

    /** One page as the reader shows it: its image or text, its words and the word boxes. */
    public record PageView(int number, boolean hasImage, String text, List<String> words,
                           List<WordBox> boxes, long savedCount) {
    }

    /**
     * Shows one page, or two side by side when the pages are portrait (book or document pages).
     * Landscape pages (slides) are wide enough to fill the screen alone.
     */
    @GetMapping("/sets/{id}/read")
    public String read(@PathVariable long id, @RequestParam(defaultValue = "1") int page, Model model)
            throws Exception {
        DocumentSet set = flashcards.set(id);
        List<DocumentReader.Page> pages = documents.pages(set);
        DocumentReader.Page current = documents.page(set, page);
        List<Integer> pageNumbers = pages.stream().map(DocumentReader.Page::number).toList();
        int index = pageNumbers.indexOf(current.number());

        boolean spread = isPortrait(current) && index + 1 < pages.size() && isPortrait(pages.get(index + 1));
        List<DocumentReader.Page> shown = spread ? pages.subList(index, index + 2) : List.of(current);
        int step = isPortrait(current) ? 2 : 1;

        // Words already in this set, so pages show them as saved (from any earlier page).
        Set<String> saved = new HashSet<>();
        flashcards.allCards(id).forEach(card -> saved.add(card.front()));

        model.addAttribute("set", set);
        model.addAttribute("page", current);
        model.addAttribute("spread", spread);
        model.addAttribute("pageCount", pages.size());
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("previews", previewWindow(pageNumbers, current.number()));
        model.addAttribute("shownNumbers", shown.stream().map(DocumentReader.Page::number).toList());
        model.addAttribute("prevPage", index == 0 ? null : pageNumbers.get(Math.max(0, index - step)));
        model.addAttribute("nextPage", index + shown.size() < pageNumbers.size()
                ? pageNumbers.get(index + shown.size()) : null);
        model.addAttribute("views", shown.stream().map(p -> pageView(p, saved)).toList());
        model.addAttribute("wordCount", shown.stream().flatMap(p -> p.words().stream()).distinct().count());
        model.addAttribute("saved", saved);
        return "reader";
    }

    private static boolean isPortrait(DocumentReader.Page page) {
        return page.image() != null && page.image().getHeight() > page.image().getWidth();
    }

    private static PageView pageView(DocumentReader.Page page, Set<String> saved) {
        return new PageView(page.number(), page.image() != null, page.text(), page.words(), boxes(page),
                page.words().stream().filter(saved::contains).count());
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

    /** At most this many slide thumbnails are shown at the bottom of the reader. */
    private static final int MAX_PREVIEWS = 10;

    /** The page numbers to show as thumbnails: up to 10, with the current page near the middle. */
    static List<Integer> previewWindow(List<Integer> pageNumbers, int current) {
        int index = Math.max(0, pageNumbers.indexOf(current));
        int from = Math.max(0, Math.min(index - MAX_PREVIEWS / 2 + 1, pageNumbers.size() - MAX_PREVIEWS));
        int to = Math.min(pageNumbers.size(), from + MAX_PREVIEWS);
        return pageNumbers.subList(from, to);
    }

    /**
     * Word boxes for words that are in the sidebar list (only PDFs have positions). A piece of
     * PDF text can hold several words (질의응답및발표자전환) or a word with endings (구현과);
     * each word gets its own box around just its characters.
     */
    private static List<WordBox> boxes(DocumentReader.Page page) {
        if (page.image() == null) {
            return List.of();
        }
        double width = page.image().getWidth();
        double height = page.image().getHeight();
        Set<String> listed = new HashSet<>(page.words());
        List<WordBox> boxes = new ArrayList<>();
        for (WordPosition position : page.positions()) {
            for (KoreanWordExtractor.Token token : KoreanWordExtractor.tokens(position.getWord())) {
                if (listed.contains(token.word())) {
                    boxes.add(toBox(token, position, width, height));
                }
            }
        }
        return boxes;
    }

    private static WordBox toBox(KoreanWordExtractor.Token token, WordPosition position,
                                 double width, double height) {
        double left = position.leftOf(token.offset(), token.length()) * PIXELS_PER_POINT;
        double boxWidth = position.widthOf(token.offset(), token.length()) * PIXELS_PER_POINT;
        double top = (position.getY() - position.getHeight()) * PIXELS_PER_POINT;
        return new WordBox(token.word(),
                percent(left, width),
                percent(top, height),
                percent(Math.max(4, boxWidth), width),
                percent(Math.max(4, position.getHeight() * PIXELS_PER_POINT * 1.3), height));
    }

    private static String percent(double value, double total) {
        return String.format(Locale.ROOT, "%.3f%%", 100 * value / total);
    }
}
