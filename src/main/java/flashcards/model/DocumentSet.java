package flashcards.model;

/**
 * A named group of flashcards. Usually created from an opened document (PDF, PPT, image),
 * but it can also come from an imported CSV/TXT file or be created empty by the user.
 */
public record DocumentSet(long id, String name, String path) {

    /** Path prefix for sets that have no source file (created with "New set"). */
    public static final String MANUAL_PREFIX = "manual:";

    /** True when the set was built from a document that the reader can open again. */
    public boolean isReadable() {
        if (path.startsWith(MANUAL_PREFIX)) {
            return false;
        }
        String lower = path.toLowerCase();
        return !(lower.endsWith(".csv") || lower.endsWith(".txt"));
    }
}
