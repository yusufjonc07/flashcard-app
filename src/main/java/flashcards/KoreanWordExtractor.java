package flashcards;

import org.openkoreantext.processor.OpenKoreanTextProcessorJava;
import org.openkoreantext.processor.KoreanTokenJava;

import java.util.ArrayList;
import java.util.List;

public class KoreanWordExtractor {

    String text;
    List<KoreanTokenJava> tokens;
    List<String> words = new ArrayList<>();

    public List<String> getWords() {
        return words;
    }

    public KoreanWordExtractor clean() {

        tokens.removeIf(token -> {
            String word = token.getText();

            return word.matches("\\d+")
                    || word.matches("[^9가-힣]+") || word.length() == 1;
        });

        words.clear();

        for (KoreanTokenJava token : tokens) {

            final String word = token.getText();

            if(!words.contains(word)){
                words.add(word);
            }
        }

        return this;
    }

    public KoreanWordExtractor extract() {
        // 1. Normalize text
        CharSequence normalized = OpenKoreanTextProcessorJava.normalize(text);

        // 2. Tokenize text
        tokens = OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
                OpenKoreanTextProcessorJava.tokenize(normalized));

        for (KoreanTokenJava token : tokens) {
            words.add(token.getText());
        }

        return this;
    }

    public KoreanWordExtractor(String rawText) {
        text = rawText;
    }
}