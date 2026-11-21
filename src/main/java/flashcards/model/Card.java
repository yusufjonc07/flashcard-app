package flashcards.model;

/**
 * One flashcard and its learning progress.
 *
 * <p>The mastery rule lives only here: {@link #answered(boolean)} returns the card's next
 * state, and {@code FlashcardRepository.saveProgress(Card)} just persists it. Keeping the rule in one
 * place means the review session, undo, and the database can never disagree about it.
 */
public record Card(long id, long documentId, String front, String translation,
                   int correct, int incorrect, int streak, boolean mastered) {

    /** Consecutive correct answers required before a card counts as mastered. */
    public static final int MASTERY_STREAK = 3;

    public enum Status {
        NEW("New"),
        HIGH_PRIORITY("High priority"),
        LEARNING("Learning"),
        MASTERED("Mastered");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Returns the state of this card after one more answer; the record itself is unchanged. */
    public Card answered(boolean wasCorrect) {
        if (wasCorrect) {
            int nextStreak = streak + 1;
            return new Card(id, documentId, front, translation, correct + 1, incorrect,
                    nextStreak, nextStreak >= MASTERY_STREAK);
        }
        return new Card(id, documentId, front, translation, correct, incorrect + 1, 0, false);
    }

    public Status status() {
        if (mastered) {
            return Status.MASTERED;
        }
        if (correct == 0 && incorrect == 0) {
            return Status.NEW;
        }
        if (streak == 0) {
            return Status.HIGH_PRIORITY;
        }
        return Status.LEARNING;
    }

    /** Human-readable status, e.g. "Learning (2/3)". */
    public String statusText() {
        return status() == Status.LEARNING
                ? status().label() + " (" + streak + "/" + MASTERY_STREAK + ")"
                : status().label();
    }
}
