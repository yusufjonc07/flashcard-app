package flashcards.web;

import flashcards.service.DocumentCache;
import flashcards.service.FlashcardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** JSON endpoints used by the reader page's JavaScript. */
@RestController
public class ReaderApiController {

    public record SaveRequest(String word, String translation) {
    }

    public record SaveResponse(boolean saved, String word) {
    }

    private final FlashcardService flashcards;
    private final DocumentCache documents;

    public ReaderApiController(FlashcardService flashcards, DocumentCache documents) {
        this.flashcards = flashcards;
        this.documents = documents;
    }

    /** Meanings of every word on a page: {"학교": "school", ...}. */
    @GetMapping("/api/sets/{id}/pages/{number}/translations")
    public Map<String, String> translations(@PathVariable long id, @PathVariable int number) throws Exception {
        return flashcards.translateAll(documents.page(flashcards.set(id), number).words());
    }

    /** Saves a revealed word to the set; saved=false means it was already there. */
    @PostMapping("/api/sets/{id}/cards")
    public SaveResponse save(@PathVariable long id, @RequestBody SaveRequest request) {
        return new SaveResponse(flashcards.saveCard(id, request.word(), request.translation()), request.word());
    }
}
