
package flashcards.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Translator {

    private static final String API_URL = "https://api.mymemory.translated.net/get";

    private final HttpClient client = HttpClient.newHttpClient();

    public String translate(String text, String targetLanguage)
            throws Exception {

        String encodedText = URLEncoder.encode(
                text, StandardCharsets.UTF_8);

        String url = API_URL
                + "?q=" + encodedText
                + "&langpair=ko%7C" + targetLanguage;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed");
        }

        String json = response.body();

        Pattern pattern = Pattern.compile(
                "\"translatedText\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return matcher.group(1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\");
        }

        throw new RuntimeException("Translation not found");
    }

    public List<String> translateAll(
            List<String> words,
            String targetLanguage) throws Exception {

        List<String> translations = new ArrayList<>();

        for (String word : words) {
            String translated = translate(word, targetLanguage);
            translations.add(translated);
        }

        return translations;
    }

}
