package flashcards.service;

import flashcards.model.Card;
import flashcards.model.DocumentSet;
import flashcards.model.ReviewSession;
import flashcards.repository.FlashcardRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Application logic shared by all web pages: sets, words, import, summaries, review
 * sessions and translation. Web controllers call only this class and {@link DocumentCache}.
 */
@Service
public class FlashcardService {

    /** Counts shown on the home page and the words page (R8). */
    public record Summary(int total, int mastered, int highPriority, int learning, int fresh,
                          int correctAnswers, int incorrectAnswers) {

        public int accuracyPercent() {
            int answers = correctAnswers + incorrectAnswers;
            return answers == 0 ? 0 : Math.round(100f * correctAnswers / answers);
        }

        public int masteredPercent() {
            return total == 0 ? 0 : Math.round(100f * mastered / total);
        }
    }

    /** A set together with its summary, for the home page. */
    public record SetOverview(DocumentSet set, Summary summary, boolean readable) {
    }

    /** Result of importing a CSV/TXT file. */
    public record ImportResult(DocumentSet set, int added, int skipped) {
    }

    private final FlashcardRepository repository;
    private final Translator translator;

    public FlashcardService(FlashcardRepository repository, Translator translator) {
        this.repository = repository;
        this.translator = translator;
    }

    // ---- Sets ----------------------------------------------------------------------------

    public List<SetOverview> overview() {
        return repository.documents().stream()
                .map(set -> new SetOverview(set, summary(set.id()), canRead(set)))
                .toList();
    }

    public DocumentSet set(long id) {
        return repository.document(id);
    }

    /** True if the set came from a document that still exists on disk. */
    public boolean canRead(DocumentSet set) {
        return set.isReadable() && Files.isRegularFile(Path.of(set.path()));
    }

    /** Registers (or finds) the set for a document or word-list file. */
    public DocumentSet registerDocument(String name, Path path) {
        String absolute = path.toAbsolutePath().toString();
        long id = repository.addDocument(name, absolute);
        return new DocumentSet(id, name, absolute);
    }

    /** Creates an empty set for words typed in by hand (R1). */
    public DocumentSet createSet(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Set name must not be blank");
        }
        String path = DocumentSet.MANUAL_PREFIX + trimmed.toLowerCase(Locale.ROOT);
        long id = repository.addDocument(trimmed, path);
        return new DocumentSet(id, trimmed, path);
    }

    // ---- Cards ---------------------------------------------------------------------------

    public List<Card> allCards(long setId) {
        return repository.allCards(setId);
    }

    /** Numbered word list filtered by a Korean or English search term (R4, R5). */
    public List<Card> searchCards(long setId, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Card> cards = repository.allCards(setId);
        if (needle.isEmpty()) {
            return cards;
        }
        return cards.stream()
                .filter(card -> card.front().toLowerCase(Locale.ROOT).contains(needle)
                        || card.translation().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /**
     * Adds a word typed by the user (R1, R2). When the meaning is left blank it is
     * translated automatically.
     *
     * @return the saved meaning, or {@code null} if the word was already in the set
     */
    public String addWord(long setId, String word, String meaning) throws Exception {
        repository.document(setId);
        String front = word == null ? "" : word.trim();
        if (front.isEmpty()) {
            throw new IllegalArgumentException("Enter a word first");
        }
        String back = meaning == null || meaning.isBlank()
                ? translateAll(List.of(front)).get(front)
                : meaning.trim();
        if (back == null) {
            throw new IllegalStateException("Could not translate \"" + front
                    + "\". Type the meaning yourself.");
        }
        return repository.addCard(setId, front, back) ? back : null;
    }

    /** Saves a word revealed in the document reader. Returns false if it was already saved. */
    public boolean saveCard(long setId, String word, String translation) {
        repository.document(setId);
        return repository.addCard(setId, word, translation);
    }

    /** Changes the meaning of a card in this set and returns the updated card. */
    public Card editMeaning(long setId, long cardId, String meaning) {
        if (repository.card(cardId).documentId() != setId) {
            throw new IllegalArgumentException("That word belongs to another set");
        }
        String back = meaning == null ? "" : meaning.trim();
        if (back.isEmpty()) {
            throw new IllegalArgumentException("The meaning cannot be empty");
        }
        repository.updateTranslation(cardId, back);
        return repository.card(cardId);
    }

    public void deleteCard(long setId, long cardId) {
        if (repository.card(cardId).documentId() != setId) {
            throw new IllegalArgumentException("That word belongs to another set");
        }
        repository.deleteCard(cardId);
    }

    /**
     * Imports a CSV/TXT word list into a set named after the file. Lines without a meaning
     * are translated first; words already in the set are skipped.
     */
    public ImportResult importFile(String name, Path path) throws Exception {
        CardImporter.Result parsed = CardImporter.read(path);
        Map<String, String> cards = new LinkedHashMap<>(parsed.cards());
        if (!parsed.untranslated().isEmpty()) {
            cards.putAll(translateAll(parsed.untranslated()));
        }
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("No words found in " + name);
        }
        DocumentSet set = registerDocument(name, path);
        int added = repository.addCards(set.id(), cards);
        return new ImportResult(set, added, cards.size() - added);
    }

    public Summary summary(long setId) {
        int mastered = 0;
        int high = 0;
        int learning = 0;
        int fresh = 0;
        int correct = 0;
        int incorrect = 0;
        List<Card> cards = repository.allCards(setId);
        for (Card card : cards) {
            switch (card.status()) {
                case MASTERED -> mastered++;
                case HIGH_PRIORITY -> high++;
                case LEARNING -> learning++;
                case NEW -> fresh++;
            }
            correct += card.correct();
            incorrect += card.incorrect();
        }
        return new Summary(cards.size(), mastered, high, learning, fresh, correct, incorrect);
    }

    // ---- Review --------------------------------------------------------------------------

    /** Starts a session with the set's unmastered cards, weakest first (R6, R9). */
    public ReviewSession startReview(long setId) {
        return startReview(setId, false);
    }

    /** Same as {@link #startReview(long)}, optionally in random order. */
    public ReviewSession startReview(long setId, boolean shuffle) {
        repository.document(setId);
        List<Card> cards = new ArrayList<>(repository.reviewCards(setId));
        if (shuffle) {
            java.util.Collections.shuffle(cards);
        }
        return new ReviewSession(cards, repository::saveProgress);
    }

    // ---- Translation ---------------------------------------------------------------------

    /**
     * Translates words, sending each word to the translation service only once.
     *
     * <p>A meaning saved on a card (possibly edited by the user) is used first, then a
     * translation fetched earlier (kept in the database per language), and only the remaining
     * words are sent to the service. New results are saved, so opening the same page again,
     * even after a restart, makes no requests. Words that could not be translated are left out
     * and tried again next time.
     */
    public Map<String, String> translateAll(List<String> words) throws Exception {
        String language = translator.targetLanguage();
        Map<String, String> known = new HashMap<>(repository.cachedTranslations(words, language));
        known.putAll(repository.knownTranslations(words));
        List<String> missing = new ArrayList<>();
        for (String word : words) {
            if (!known.containsKey(word) && !missing.contains(word)) {
                missing.add(word);
            }
        }
        if (!missing.isEmpty()) {
            List<String> translated = translator.translateAll(missing);
            Map<String, String> fresh = new LinkedHashMap<>();
            for (int i = 0; i < missing.size() && i < translated.size(); i++) {
                String meaning = translated.get(i);
                if (meaning != null && !meaning.isBlank()) {
                    fresh.put(missing.get(i), meaning);
                }
            }
            repository.cacheTranslations(fresh, language);
            known.putAll(fresh);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String word : words) {
            String meaning = known.get(word);
            if (meaning != null && !meaning.isBlank()) {
                result.put(word, meaning);
            }
        }
        return result;
    }
}
