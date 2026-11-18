package flashcards.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * One study session over a set's unmastered cards.
 *
 * <p>Owns the session's state: the queue of cards still to answer and simple counters.
 * Card progress itself is computed by {@link Card#answered(boolean)} and written through a
 * {@link ProgressSaver} before the session changes, so a failed save leaves both the
 * database and the session as they were.
 *
 * <ul>
 *   <li>Correct answer: the card leaves this session. It comes back in later sessions until
 *       it has been answered correctly {@value Card#MASTERY_STREAK} times in a row.</li>
 *   <li>Incorrect or blank answer: the card becomes high priority and moves to the end of
 *       the queue, so it stays in the active rotation until it is answered correctly.</li>
 * </ul>
 */
public final class ReviewSession {

    /** Persists a card's new progress. Implemented by the controller with SQLite. */
    @FunctionalInterface
    public interface ProgressSaver {
        void save(Card card) throws Exception;
    }

    /** What happened to one answer; {@code before} is kept so the view can show the change. */
    public record AnswerResult(Card before, Card after, boolean correct, String given) {

        public String expected() {
            return before.translation();
        }

        public boolean justMastered() {
            return after.mastered() && !before.mastered();
        }
    }

    private record Snapshot(Card card, List<Card> queue, int answered, int correctAnswers,
                            int masteredNow) {
    }

    private final Deque<Card> queue;
    private final ProgressSaver saver;
    private final int total;
    private Snapshot undo;
    private int answered;
    private int correctAnswers;
    private int masteredNow;

    public ReviewSession(List<Card> cards, ProgressSaver saver) {
        this.queue = new ArrayDeque<>(cards);
        this.saver = saver;
        this.total = cards.size();
    }

    public Card current() {
        return queue.peekFirst();
    }

    public boolean isFinished() {
        return queue.isEmpty();
    }

    /** Checks a typed answer against the card's translation (R1, R2). */
    public AnswerResult submit(String typedAnswer) throws Exception {
        Card card = requireCurrent();
        return apply(card, matches(typedAnswer, card.translation()),
                typedAnswer == null ? "" : typedAnswer.trim());
    }

    /** Self-graded answer, used after "Show answer" (R7). */
    public AnswerResult grade(boolean knewIt) throws Exception {
        return apply(requireCurrent(), knewIt, null);
    }

    /** Reverts the most recent answer in both the database and the queue. */
    public boolean undo() throws Exception {
        if (undo == null) {
            return false;
        }
        saver.save(undo.card());
        queue.clear();
        queue.addAll(undo.queue());
        answered = undo.answered();
        correctAnswers = undo.correctAnswers();
        masteredNow = undo.masteredNow();
        undo = null;
        return true;
    }

    /** Replaces the current card after its meaning was edited; progress is unchanged. */
    public void replaceCurrent(Card edited) {
        Card card = requireCurrent();
        if (card.id() != edited.id()) {
            throw new IllegalArgumentException("Only the current card can be edited");
        }
        queue.removeFirst();
        queue.addFirst(edited);
    }

    public boolean canUndo() {
        return undo != null;
    }

    public int remaining() {
        return queue.size();
    }

    public int total() {
        return total;
    }

    public int answered() {
        return answered;
    }

    public int correctAnswers() {
        return correctAnswers;
    }

    public int masteredThisSession() {
        return masteredNow;
    }

    /**
     * Compares answers case-insensitively, ignoring extra spaces, surrounding punctuation and
     * a leading article or "to". A translation like "school, academy" accepts either word.
     * A blank answer is never correct.
     */
    public static boolean matches(String typed, String translation) {
        String answer = normalize(typed);
        if (answer.isEmpty() || translation == null) {
            return false;
        }
        if (answer.equals(normalize(translation))) {
            return true;
        }
        for (String option : translation.split("[,;/]")) {
            if (answer.equals(normalize(option))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String result = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
        return result.replaceFirst("^(a|an|the|to) ", "");
    }

    private AnswerResult apply(Card card, boolean correct, String given) throws Exception {
        Card updated = card.answered(correct);
        saver.save(updated);

        undo = new Snapshot(card, new ArrayList<>(queue), answered, correctAnswers, masteredNow);
        queue.removeFirst();
        if (!correct) {
            queue.addLast(updated);
        }
        answered++;
        if (correct) {
            correctAnswers++;
        }
        if (updated.mastered() && !card.mastered()) {
            masteredNow++;
        }
        return new AnswerResult(card, updated, correct, given);
    }

    private Card requireCurrent() {
        if (queue.isEmpty()) {
            throw new IllegalStateException("No cards left in this session");
        }
        return queue.peekFirst();
    }
}
