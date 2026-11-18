package flashcards.service;

import org.openkoreantext.processor.KoreanPosJava;
import org.openkoreantext.processor.KoreanTokenJava;
import org.openkoreantext.processor.OpenKoreanTextProcessorJava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the Korean vocabulary words in a text.
 *
 * <p>The text is split with open-korean-text, which also tags each piece with its part of
 * speech. Only content words are kept (nouns, verbs, adjectives, adverbs); particles (을, 과),
 * endings (하고), numbers, dates, e-mail addresses and Latin text are dropped. Verbs and
 * adjectives are returned in dictionary form, so 연결하고 and 연결할 both become 연결하다.
 *
 * <p>{@link #tokens(String)} also returns where each word is in the text, which the reader
 * uses to draw one highlight per word even when a line has no spaces (질의응답및발표자전환).
 */
public final class KoreanWordExtractor {

    /** A vocabulary word found in a text: its dictionary form and where it appears. */
    public record Token(String word, int offset, int length) {
    }

    private static final Set<KoreanPosJava> CONTENT = EnumSet.of(
            KoreanPosJava.Noun, KoreanPosJava.ProperNoun, KoreanPosJava.Verb,
            KoreanPosJava.Adjective, KoreanPosJava.Adverb);

    /** Very common words that carry no meaning of their own (하다 in 구현할, 이다 in 것이다). */
    private static final Set<String> STOP_WORDS = Set.of(
            "하다", "되다", "이다", "있다", "없다", "않다", "같다", "보다", "주다", "그리고", "또는");

    /** Shorter words are mostly fragments; a one-syllable noun like 값 is rarely worth a card. */
    private static final int MIN_LENGTH = 2;

    static {
        loadExtraNouns();
    }

    private KoreanWordExtractor() {
    }

    /** The distinct vocabulary words of a text, in the order they first appear. */
    public static List<String> words(String text) {
        Set<String> words = new LinkedHashSet<>();
        for (Token token : tokens(text)) {
            words.add(token.word());
        }
        return new ArrayList<>(words);
    }

    /** Every vocabulary word of a text with its position, in reading order (repeats included). */
    public static List<Token> tokens(String text) {
        List<Token> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        // No normalize(): it can change the text, and the offsets must match the original.
        List<KoreanTokenJava> tokens = OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
                OpenKoreanTextProcessorJava.tokenize(text));
        for (KoreanTokenJava token : tokens) {
            if (!CONTENT.contains(token.getPos())) {
                continue;
            }
            String stem = token.getStem();
            String word = stem == null || stem.isEmpty() ? token.getText() : stem;
            if (isVocabulary(word)) {
                result.add(new Token(word, token.getOffset(), token.getLength()));
            }
        }
        return result;
    }

    private static boolean isVocabulary(String word) {
        return word.length() >= MIN_LENGTH
                && word.chars().allMatch(c -> c >= '가' && c <= '힣')
                && !STOP_WORDS.contains(word);
    }

    /**
     * Loads src/main/resources/korean-nouns.txt (one noun per line, # for comments): words the
     * tokenizer does not know, such as loanwords from lectures (메소드 would otherwise be 메 + 소드).
     */
    private static void loadExtraNouns() {
        InputStream input = KoreanWordExtractor.class.getResourceAsStream("/korean-nouns.txt");
        if (input == null) {
            return;
        }
        List<String> nouns = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    nouns.add(line);
                }
            }
        } catch (IOException ignored) {
            return;   // the extra words are optional
        }
        if (!nouns.isEmpty()) {
            OpenKoreanTextProcessorJava.addNounsToDictionary(nouns);
        }
    }
}
