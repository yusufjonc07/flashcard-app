package flashcards.controller;

import flashcards.model.SQLiteStore;
import flashcards.service.DocumentReader;
import flashcards.service.Translator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class AppController {

    private final SQLiteStore store;
    private final Translator translator;

    public AppController(String databasePath) throws Exception {
        store = new SQLiteStore(databasePath);
        translator = new Translator();
    }

    public List<SQLiteStore.Document> documents() throws Exception {
        return store.documents();
    }

    public List<SQLiteStore.Card> allCards(Long documentId) throws Exception {
        return store.allCards(documentId);
    }

    public List<SQLiteStore.Card> reviewCards(Long documentId) throws Exception {
        return store.reviewCards(documentId);
    }

    public List<DocumentReader.Page> readDocument(Path path) throws Exception {
        return DocumentReader.read(path);
    }

    public Map<String, String> translateAll(List<String> words) throws Exception {
        List<String> translated = translator.translateAll(words, "en");
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < words.size() && i < translated.size(); i++) {
            result.put(words.get(i), translated.get(i));
        }
        return result;
    }

    public long registerDocument(String name, String path) throws Exception {
        return store.addDocument(name, path);
    }

    public void recordAnswer(long id, boolean correct) throws Exception {
        store.recordAnswer(id, correct);
    }

    public void restoreProgress(SQLiteStore.Card card) throws Exception {
        store.restoreProgress(card);
    }

    public void saveCard(long documentId, String word, String translation) throws Exception {
        store.addCard(documentId, word, translation);
    }

}
