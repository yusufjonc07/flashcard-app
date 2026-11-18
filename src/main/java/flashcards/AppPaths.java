package flashcards;

import java.nio.file.Path;

/**
 * Where the app keeps its files. By default these are the relative folders {@code data/} and
 * {@code tessdata/} (run from the project folder). The desktop app points them elsewhere with the
 * system properties {@code flashcards.data} and {@code flashcards.tessdata}.
 */
public final class AppPaths {

    private AppPaths() {
    }

    /** Folder for the SQLite database and uploaded documents. */
    public static Path data() {
        return Path.of(System.getProperty("flashcards.data", "data"));
    }

    /** Folder with the Tesseract language data (kor, eng). */
    public static Path tessdata() {
        return Path.of(System.getProperty("flashcards.tessdata", "tessdata"));
    }
}
