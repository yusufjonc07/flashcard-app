package flashcards;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SQLiteStore {

    public record Document(long id, String name, String path) {
    }

    public record Card(long id, String front, String translation, int correct, int incorrect,
                       int streak, boolean mastered) {
    }

    private final Path databasePath;

    public SQLiteStore(String pathname) throws IOException, SQLException {
        databasePath = Path.of(pathname);
        Path parent = databasePath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    path TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE IF NOT EXISTS cards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    front TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    translation TEXT NOT NULL,
                    correct INTEGER NOT NULL DEFAULT 0,
                    incorrect INTEGER NOT NULL DEFAULT 0,
                    streak INTEGER NOT NULL DEFAULT 0,
                    mastered INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """);
        if (!query("PRAGMA table_info(cards);").contains("document_id")) {
            execute("ALTER TABLE cards ADD COLUMN document_id INTEGER REFERENCES documents(id);");
        }
        if (query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'cards';")
                .contains("front TEXT NOT NULL UNIQUE")) {
            execute("""
                    CREATE TABLE cards_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        front TEXT NOT NULL,
                        translation TEXT NOT NULL,
                        correct INTEGER NOT NULL DEFAULT 0,
                        incorrect INTEGER NOT NULL DEFAULT 0,
                        streak INTEGER NOT NULL DEFAULT 0,
                        mastered INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        document_id INTEGER REFERENCES documents(id),
                        UNIQUE(document_id, front)
                    );
                    INSERT INTO cards_new(id, front, translation, correct, incorrect, streak, mastered,
                                          created_at, document_id)
                    SELECT id, front, translation, correct, incorrect, streak, mastered, created_at, document_id
                    FROM cards;
                    DROP TABLE cards;
                    ALTER TABLE cards_new RENAME TO cards;
                    """);
        }
        execute("""
                CREATE INDEX IF NOT EXISTS cards_document_idx ON cards(document_id);
                """);
    }

    public void addCard(String front, String translation) throws IOException, SQLException {
        addCard(null, front, translation);
    }

    public void addCard(Long documentId, String front, String translation)
            throws IOException, SQLException {
        String normalizedFront = front == null ? "" : front.trim();
        String normalizedTranslation = translation == null ? "" : translation.trim();
        if (normalizedFront.isEmpty() || normalizedTranslation.isEmpty()) {
            throw new IllegalArgumentException("Card word and translation must not be blank");
        }

        String document = documentId == null ? "NULL" : documentId.toString();
        String match = documentId == null ? "document_id IS NULL" : "document_id = " + documentId;
        execute("INSERT INTO cards(front, translation, document_id) "
                + "SELECT " + quote(normalizedFront) + ", " + quote(normalizedTranslation) + ", " + document
                + " WHERE NOT EXISTS (SELECT 1 FROM cards WHERE " + match
                + " AND front = " + quote(normalizedFront) + ");");
    }

    public long addDocument(String name, String path) throws IOException, SQLException {
        execute("INSERT OR IGNORE INTO documents(name, path) VALUES("
                + quote(name) + ", " + quote(path) + ");");
        return Long.parseLong(query("SELECT id FROM documents WHERE path = " + quote(path) + ";").trim());
    }

    public List<Document> documents() throws IOException, SQLException {
        String output = query("SELECT id, hex(name), hex(path) FROM documents ORDER BY id;");
        List<Document> result = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                String[] fields = line.split("\\t", -1);
                result.add(new Document(Long.parseLong(fields[0]), decodeHex(fields[1]), decodeHex(fields[2])));
            }
        }
        return result;
    }

    public boolean hasCard(Long documentId, String front) throws IOException, SQLException {
        String document = documentId == null ? "IS NULL" : "= " + documentId;
        return "1".equals(query("SELECT EXISTS(SELECT 1 FROM cards WHERE document_id " + document
                + " AND front = " + quote(front.trim()) + ");").trim());
    }

    public boolean hasCard(String front) throws IOException, SQLException {
        String output = query("SELECT EXISTS(SELECT 1 FROM cards WHERE front = "
                + quote(front.trim()) + ");");
        return "1".equals(output.trim());
    }

    public List<Card> reviewCards() throws IOException, SQLException {
        return reviewCards(null);
    }

    public List<Card> reviewCards(Long documentId) throws IOException, SQLException {
        String filter = documentId == null ? "" : "AND document_id = " + documentId;
        String output = query("""
                SELECT id, hex(front), hex(translation), correct, incorrect, streak, mastered
                FROM cards
                WHERE mastered = 0 %s
                ORDER BY incorrect DESC, streak ASC, id ASC;
                """.formatted(filter));
        return parseCards(output);
    }

    public List<Card> allCards() throws IOException, SQLException {
        return allCards(null);
    }

    public List<Card> allCards(Long documentId) throws IOException, SQLException {
        String filter = documentId == null ? "" : "WHERE document_id = " + documentId;
        String output = query("""
                SELECT id, hex(front), hex(translation), correct, incorrect, streak, mastered
                FROM cards %s
                ORDER BY id ASC;
                """.formatted(filter));
        return parseCards(output);
    }

    public void recordAnswer(long id, boolean correct) throws IOException, SQLException {
        String sql = correct
                ? "UPDATE cards SET correct = correct + 1, streak = streak + 1, "
                    + "mastered = CASE WHEN streak + 1 >= 3 THEN 1 ELSE 0 END WHERE id = " + id + ";"
                : "UPDATE cards SET incorrect = incorrect + 1, streak = 0, mastered = 0 WHERE id = " + id + ";";
        execute(sql);
    }

    public void restoreProgress(Card card) throws IOException, SQLException {
        execute("UPDATE cards SET correct = " + card.correct()
                + ", incorrect = " + card.incorrect()
                + ", streak = " + card.streak()
                + ", mastered = " + (card.mastered() ? 1 : 0)
                + " WHERE id = " + card.id() + ";");
    }

    private List<Card> parseCards(String output) {
        List<Card> cards = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 7) {
                throw new IllegalStateException("Unexpected SQLite result: " + line);
            }
            cards.add(new Card(
                    Long.parseLong(fields[0]),
                    decodeHex(fields[1]),
                    decodeHex(fields[2]),
                    Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[4]),
                    Integer.parseInt(fields[5]),
                    "1".equals(fields[6])));
        }
        return cards;
    }

    private String decodeHex(String value) {
        if (value.isEmpty()) {
            return "";
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void execute(String sql) throws IOException, SQLException {
        run(sql, false);
    }

    private String query(String sql) throws IOException, SQLException {
        return run(".headers off\n.mode tabs\n" + sql, true);
    }

    private String run(String sql, boolean captureOutput) throws IOException, SQLException {
        Process process = new ProcessBuilder("sqlite3", "-batch", databasePath.toString())
                .redirectErrorStream(false)
                .start();
        try (BufferedWriter input = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            input.write(sql);
            input.newLine();
        }

        StringBuilder errors = new StringBuilder();
        Thread errorReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> errors.append(line).append(System.lineSeparator()));
            } catch (IOException ignored) {
                // The process exit code remains the source of truth.
            }
        });
        errorReader.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (all, line) -> all + line + System.lineSeparator());
        }
        try {
            errorReader.join();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new SQLException(errors.toString().trim());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while accessing SQLite", exception);
        }
        return captureOutput ? output : "";
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
