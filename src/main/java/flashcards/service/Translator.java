package flashcards.service;

import java.util.List;

/** Translates Korean words. The interface lets tests use a fake instead of the network. */
public interface Translator {

    /** Returns one translation per input word, in the same order. */
    List<String> translateAll(List<String> words) throws Exception;

    /** Language code of the translations, e.g. "en". Saved translations are kept per language. */
    default String targetLanguage() {
        return "en";
    }
}
