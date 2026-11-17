package flashcards.repository;

import flashcards.model.Card;
import flashcards.model.DocumentSet;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves vocabulary sets and flashcards in SQLite ({@code data/flashcards.db}).
 * Every change is written immediately, so progress survives closing the program at any
 * point (requirement R10).
 */
@Repository
public class FlashcardRepository {

    private static final String SELECT_CARD = """
            SELECT id, IFNULL(document_id, 0) AS document_id, front, translation,
                   correct, incorrect, streak, mastered
            FROM cards
            """;

    private static final RowMapper<Card> CARD = (row, number) -> new Card(
            row.getLong("id"),
            row.getLong("document_id"),
            row.getString("front"),
            row.getString("translation"),
            row.getInt("correct"),
            row.getInt("incorrect"),
            row.getInt("streak"),
            row.getInt("mastered") == 1);

    private static final RowMapper<DocumentSet> DOCUMENT = (row, number) ->
            new DocumentSet(row.getLong("id"), row.getString("name"), row.getString("path"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate transactions;

    public FlashcardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        createSchema();
    }

    // ---- Sets ----------------------------------------------------------------------------

    /** Creates the set for this path, or returns the existing one's id. */
    public long addDocument(String name, String path) {
        jdbc.update("INSERT OR IGNORE INTO documents(name, path) VALUES (?, ?)", name, path);
        return jdbc.queryForObject("SELECT id FROM documents WHERE path = ?", Long.class, path);
    }

    public List<DocumentSet> documents() {
        return jdbc.query("SELECT id, name, path FROM documents ORDER BY id", DOCUMENT);
    }

    public DocumentSet document(long id) {
        try {
            return jdbc.queryForObject("SELECT id, name, path FROM documents WHERE id = ?", DOCUMENT, id);
        } catch (EmptyResultDataAccessException missing) {
            throw new NotFoundException("Set " + id + " does not exist");
        }
    }

    // ---- Cards ---------------------------------------------------------------------------

    /** Adds a card unless the same word is already in that set. Returns true if it was added. */
    public boolean addCard(long documentId, String front, String translation) {
        return addCards(documentId, Map.of(front == null ? "" : front,
                translation == null ? "" : translation)) == 1;
    }

    /**
     * Adds several cards to one set in a single transaction. Words already in the set are
     * skipped, so importing the same file twice does not create duplicates.
     *
     * @return the number of cards actually inserted
     */
    public int addCards(long documentId, Map<String, String> cards) {
        for (Map.Entry<String, String> card : cards.entrySet()) {
            if (card.getKey() == null || card.getKey().isBlank()
                    || card.getValue() == null || card.getValue().isBlank()) {
                throw new IllegalArgumentException("Card word and translation must not be blank");
            }
        }
        Integer inserted = transactions.execute(status -> {
            int count = 0;
            for (Map.Entry<String, String> card : cards.entrySet()) {
                count += jdbc.update("INSERT OR IGNORE INTO cards(front, translation, document_id) "
                        + "VALUES (?, ?, ?)", card.getKey().trim(), card.getValue().trim(), documentId);
            }
            return count;
        });
        return inserted == null ? 0 : inserted;
    }

    public boolean hasCard(long documentId, String front) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM cards WHERE document_id = ? AND front = ?)",
                Boolean.class, documentId, front.trim()));
    }

    /**
     * Looks up translations already saved in any set, so the same word is not sent to the
     * translation service twice.
     */
    public Map<String, String> knownTranslations(Collection<String> words) {
        Map<String, String> result = new HashMap<>();
        if (words.isEmpty()) {
            return result;
        }
        named.query("SELECT front, MIN(translation) AS translation FROM cards "
                        + "WHERE front IN (:words) GROUP BY front",
                new MapSqlParameterSource("words", words),
                row -> {
                    result.put(row.getString("front"), row.getString("translation"));
                });
        return result;
    }

    /**
     * Cards that still need review, weakest first: most wrong answers, then shortest
     * streak, then oldest.
     */
    public List<Card> reviewCards(long documentId) {
        return jdbc.query(SELECT_CARD + "WHERE mastered = 0 AND document_id = ? "
                + "ORDER BY incorrect DESC, streak ASC, id ASC", CARD, documentId);
    }

    public List<Card> allCards(long documentId) {
        return jdbc.query(SELECT_CARD + "WHERE document_id = ? ORDER BY id ASC", CARD, documentId);
    }

    public Card card(long id) {
        try {
            return jdbc.queryForObject(SELECT_CARD + "WHERE id = ?", CARD, id);
        } catch (EmptyResultDataAccessException missing) {
            throw new NotFoundException("Card " + id + " does not exist");
        }
    }

    /** Writes the card's progress fields; used after every answer and by undo. */
    public void saveProgress(Card card) {
        jdbc.update("UPDATE cards SET correct = ?, incorrect = ?, streak = ?, mastered = ? WHERE id = ?",
                card.correct(), card.incorrect(), card.streak(), card.mastered() ? 1 : 0, card.id());
    }

    public void deleteCard(long id) {
        jdbc.update("DELETE FROM cards WHERE id = ?", id);
    }

    // ---- Schema --------------------------------------------------------------------------

    /** Creates the tables, and upgrades databases made by earlier versions of the app. */
    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    path TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cards (
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
                )""");

        // The first version had no document_id column.
        boolean hasDocumentColumn = jdbc.queryForList("PRAGMA table_info(cards)").stream()
                .anyMatch(column -> "document_id".equals(column.get("name")));
        if (!hasDocumentColumn) {
            jdbc.execute("ALTER TABLE cards ADD COLUMN document_id INTEGER REFERENCES documents(id)");
        }
        // The first version made a word unique across all sets; now it is unique per set.
        String cardsSql = jdbc.queryForObject(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'cards'", String.class);
        if (cardsSql != null && cardsSql.contains("front TEXT NOT NULL UNIQUE")) {
            transactions.executeWithoutResult(status -> {
                jdbc.execute("""
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
                        )""");
                jdbc.execute("""
                        INSERT INTO cards_new(id, front, translation, correct, incorrect, streak,
                                              mastered, created_at, document_id)
                        SELECT id, front, translation, correct, incorrect, streak, mastered,
                               created_at, document_id
                        FROM cards""");
                jdbc.execute("DROP TABLE cards");
                jdbc.execute("ALTER TABLE cards_new RENAME TO cards");
            });
        }
        jdbc.execute("CREATE INDEX IF NOT EXISTS cards_document_idx ON cards(document_id)");
    }
}
