package flashcards;

import org.openkoreantext.processor.OpenKoreanTextProcessorJava;
import org.openkoreantext.processor.KoreanTokenJava;

import java.util.ArrayList;
import java.util.List;

public class KoreanWordExtractor {

    String text;
    List<String> words = new ArrayList<>();

    public static void main(String[] args) {

    }

    public List<String> extract() {
        // 1. Normalize text
        CharSequence normalized = OpenKoreanTextProcessorJava.normalize(text);

        // 2. Tokenize text
        List<KoreanTokenJava> tokens = OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
                OpenKoreanTextProcessorJava.tokenize(normalized));

        for (KoreanTokenJava token : tokens) {
            words.add(token.getText());
        }
        return words;
    }

    public KoreanWordExtractor(String rawText) {
        text = rawText;
    }
}