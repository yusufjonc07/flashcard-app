package flashcards;

import flashcards.model.Card;
import flashcards.model.DocumentSet;
import flashcards.model.ReviewSession;
import flashcards.repository.FlashcardRepository;
import flashcards.service.CardImporter;
import flashcards.service.FlashcardService;
import flashcards.service.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance criteria in README.md (AC1–AC3) and the supporting behaviour, checked on
 * the service and model layer. Each test gets a fresh SQLite file and a fake translator,
 * so no network is needed.
 */
class AcceptanceTest {

    /** Knows a few words and counts how many it was asked to translate. */
    static final class FakeTranslator implements Translator {
        private final Map<String, String> dictionary = Map.of(
                "학교", "school", "사과", "apple", "도서관", "library", "시험", "exam");
        int requests;

        @Override
        public List<String> translateAll(List<String> words) {
            requests += words.size();
            return words.stream().map(word -> dictionary.getOrDefault(word, "")).toList();
        }
    }

    @TempDir
    Path folder;
    FlashcardRepository repository;
    FakeTranslator translator;
    FlashcardService service;

    @BeforeEach
    void setUp() {
        open(folder.resolve("test.db"));
    }

    /** Opens (or re-opens) a database file, like starting the program. */
    private void open(Path db) {
        repository = new FlashcardRepository(new JdbcTemplate(
                new SingleConnectionDataSource("jdbc:sqlite:" + db, true)));
        translator = new FakeTranslator();
        service = new FlashcardService(repository, translator);
    }

    private long set(String name) {
        return service.createSet(name).id();
    }

    private Card card(long set, String front) {
        return repository.allCards(set).stream().filter(c -> c.front().equals(front)).findFirst()
                .orElseThrow(() -> new AssertionError("no card " + front));
    }

    // ---- Acceptance criteria ---------------------------------------------------------------

    @Test
    @DisplayName("AC1: the 3rd consecutive correct typed answer masters the word")
    void correctAnswerMastersCard() throws Exception {
        long set = set("lecture");
        repository.addCard(set, "학교", "school");
        repository.addCard(set, "사과", "apple");
        Card school = card(set, "학교");
        repository.saveProgress(new Card(school.id(), set, "학교", "school", 2, 0, 2, false));

        ReviewSession session = service.startReview(set);
        while (!session.current().front().equals("학교")) {
            session.submit("apple");
        }
        ReviewSession.AnswerResult result = session.submit("School");

        assertTrue(result.correct(), "answer accepted case-insensitively");
        assertTrue(result.justMastered(), "reported as newly mastered");
        assertEquals(Card.Status.MASTERED, card(set, "학교").status());
        assertEquals(List.of("사과"), repository.reviewCards(set).stream().map(Card::front).toList(),
                "학교 left the review queue");
    }

    @Test
    @DisplayName("AC1: answers match ignoring case, spaces, punctuation and listed alternatives")
    void answerMatching() {
        assertTrue(ReviewSession.matches("School", "school"));
        assertTrue(ReviewSession.matches("  school. ", "school"));
        assertTrue(ReviewSession.matches("academy", "school, academy"));
        assertTrue(ReviewSession.matches("study", "to study"));
        assertFalse(ReviewSession.matches("banana", "apple"));
        assertFalse(ReviewSession.matches("", "apple"));
        assertFalse(ReviewSession.matches(null, "apple"));
    }

    @Test
    @DisplayName("AC2: a wrong answer shows the meaning, marks high priority and comes back")
    void wrongAnswerStaysInRotation() throws Exception {
        long set = set("fruit");
        repository.addCard(set, "사과", "apple");
        repository.addCard(set, "학교", "school");

        ReviewSession session = service.startReview(set);
        assertEquals("사과", session.current().front());
        ReviewSession.AnswerResult result = session.submit("banana");

        assertFalse(result.correct());
        assertEquals("apple", result.expected());
        assertEquals("banana", result.given());
        assertEquals(Card.Status.HIGH_PRIORITY, card(set, "사과").status());
        assertEquals(1, card(set, "사과").incorrect());
        assertEquals("학교", session.current().front(), "next word shown");
        session.submit("school");
        assertEquals("사과", session.current().front(), "사과 comes back in the same session");
        assertEquals("사과", repository.reviewCards(set).get(0).front(), "first in the next session");
    }

    @Test
    @DisplayName("AC2: a blank answer counts as incorrect")
    void blankAnswerIsIncorrect() throws Exception {
        long set = set("blank");
        repository.addCard(set, "사과", "apple");
        ReviewSession session = service.startReview(set);
        ReviewSession.AnswerResult result = session.submit("   ");
        assertFalse(result.correct());
        assertEquals("", result.given());
        assertEquals(1, card(set, "사과").incorrect());
        assertFalse(session.isFinished(), "card stays in rotation");
    }

    @Test
    @DisplayName("AC3: mastered words stay mastered after restarting the program")
    void progressSurvivesRestart() throws Exception {
        Path db = folder.resolve("restart.db");
        open(db);
        long set = set("restart");
        String[][] words = {{"학교", "school"}, {"사과", "apple"}, {"도서관", "library"},
                {"시험", "exam"}, {"숙제", "homework"}, {"교수", "professor"}};
        for (String[] word : words) {
            repository.addCard(set, word[0], word[1]);
        }
        for (int round = 0; round < Card.MASTERY_STREAK; round++) {
            ReviewSession session = service.startReview(set);
            // Everything right except 교수; stop when only 교수 is left (user leaves the page).
            while (!session.isFinished() && !(session.remaining() == 1
                    && session.current().front().equals("교수"))) {
                Card current = session.current();
                session.submit(current.front().equals("교수") ? "wrong" : current.translation());
            }
        }

        open(db);   // "restart": new objects reading the same file
        assertEquals(5, repository.allCards(set).stream().filter(Card::mastered).count());
        assertEquals(List.of("교수"), repository.reviewCards(set).stream().map(Card::front).toList());
    }

    // ---- Supporting behaviour ------------------------------------------------------------

    @Test
    @DisplayName("Review order: most mistakes, then shortest streak, then oldest")
    void weakestFirst() {
        long set = set("order");
        repository.addCard(set, "가", "a1");
        repository.addCard(set, "나", "b1");
        repository.addCard(set, "다", "c1");
        repository.saveProgress(new Card(card(set, "가").id(), set, "가", "a1", 2, 0, 2, false));
        repository.saveProgress(new Card(card(set, "다").id(), set, "다", "c1", 0, 3, 0, false));
        assertEquals(List.of("다", "나", "가"), repository.reviewCards(set).stream().map(Card::front).toList());
    }

    @Test
    @DisplayName("Undo restores the database and the session queue")
    void undoRestoresState() throws Exception {
        long set = set("undo");
        repository.addCard(set, "사과", "apple");
        repository.addCard(set, "학교", "school");
        ReviewSession session = service.startReview(set);
        session.submit("wrong");
        assertTrue(session.undo());
        assertEquals(Card.Status.NEW, card(set, "사과").status());
        assertEquals("사과", session.current().front());
        assertEquals(2, session.remaining());
        assertEquals(0, session.answered());
        assertFalse(session.undo(), "one level of undo");
    }

    @Test
    @DisplayName("A failed save leaves the session unchanged")
    void failedSaveKeepsSession() {
        Card card = new Card(1, 1, "사과", "apple", 0, 0, 0, false);
        ReviewSession session = new ReviewSession(List.of(card), saved -> {
            throw new IllegalStateException("disk full");
        });
        assertThrows(IllegalStateException.class, () -> session.submit("apple"));
        assertSame(card, session.current());
        assertEquals(0, session.answered());
    }

    @Test
    @DisplayName("CSV import: header, quotes, duplicates and auto-translation")
    void csvImport() throws Exception {
        Path csv = folder.resolve("words.csv");
        Files.writeString(csv, """
                ﻿front,back
                학교,school
                "교수","professor, teacher"

                # comment
                학교,duplicate
                도서관,
                """, StandardCharsets.UTF_8);
        FlashcardService.ImportResult result = service.importFile("words.csv", csv);
        long set = result.set().id();
        assertEquals(3, result.added());
        assertEquals("professor, teacher", card(set, "교수").translation());
        assertEquals("school", card(set, "학교").translation());
        assertEquals("library", card(set, "도서관").translation(), "missing meaning translated");

        FlashcardService.ImportResult again = service.importFile("words.csv", csv);
        assertEquals(set, again.set().id());
        assertEquals(0, again.added());
        assertEquals(3, again.skipped());
    }

    @Test
    @DisplayName("TXT import: separators and words without a meaning")
    void txtImport() {
        CardImporter.Result result = CardImporter.parseText(List.of(
                "학교\tschool", "사과 - apple", "시험,exam", "도서관", "", "# note"));
        assertEquals(Map.of("학교", "school", "사과", "apple", "시험", "exam"), result.cards());
        assertEquals(List.of("도서관"), result.untranslated());
    }

    @Test
    @DisplayName("A word can be in two sets but only once per set")
    void perSetUniqueness() {
        long first = set("one");
        long second = set("two");
        assertTrue(repository.addCard(first, "학교", "school"));
        assertFalse(repository.addCard(first, "학교", "school"));
        assertTrue(repository.addCard(second, "학교", "school"));
        assertTrue(repository.hasCard(second, "학교"));
    }

    @Test
    @DisplayName("Korean text, quotes and tabs round-trip through SQLite")
    void unicodeRoundTrip() {
        long set = set("unicode");
        repository.addCard(set, "학생's \"식당\"", "student's 'cafeteria'\tline");
        Card card = repository.allCards(set).get(0);
        assertEquals("학생's \"식당\"", card.front());
        assertEquals("student's 'cafeteria'\tline", card.translation());
    }

    @Test
    @DisplayName("Peer review (KIM YEONHO): meanings are translated automatically, saved ones reused")
    void automaticTranslation() throws Exception {
        long set = set("auto");
        repository.addCard(set, "학교", "school (saved)");
        Map<String, String> result = service.translateAll(List.of("학교", "사과", "사과"));
        assertEquals("school (saved)", result.get("학교"));
        assertEquals("apple", result.get("사과"));
        assertEquals(1, translator.requests, "only the new word is sent to the translator");
        assertEquals("library", service.addWord(set, "도서관", ""), "blank meaning is translated");
    }

    @Test
    @DisplayName("Manual set: add, duplicate, untranslatable word, search, summary, delete")
    void manualSet() throws Exception {
        DocumentSet set = service.createSet("  My words ");
        assertEquals("My words", set.name());
        assertFalse(set.isReadable());
        assertEquals(set.id(), service.createSet("my words").id());
        assertEquals("apple", service.addWord(set.id(), "사과", ""));
        assertEquals("institution", service.addWord(set.id(), "학교", "institution"));
        assertNull(service.addWord(set.id(), "사과", "x"), "duplicate reported");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.addWord(set.id(), "모르는말", ""));
        assertTrue(failure.getMessage().contains("Type the meaning"));
        assertEquals(1, service.searchCards(set.id(), "APP").size());
        assertEquals(1, service.searchCards(set.id(), "학").size());
        assertEquals(2, service.summary(set.id()).fresh());
        service.deleteCard(set.id(), card(set.id(), "사과").id());
        assertEquals(1, service.allCards(set.id()).size());
    }
}
