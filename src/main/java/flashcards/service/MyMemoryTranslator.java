package flashcards.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Korean → target-language translation through the free MyMemory API
 * (https://mymemory.translated.net). No API key is needed.
 *
 * <p>The target language comes from the {@code TRANSLATE_TARGET_LANGUAGE} environment
 * variable (or {@code application.properties}) and defaults to English. Each request is retried a few times because the free
 * service sometimes fails transiently.
 */
@Component
public class MyMemoryTranslator implements Translator {

    private static final String API_URL = "https://api.mymemory.translated.net/get";
    private static final int ATTEMPTS = 3;
    private static final int PARALLEL_REQUESTS = 4;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String targetLanguage;

    public MyMemoryTranslator(@Value("${TRANSLATE_TARGET_LANGUAGE:en}") String targetLanguage) {
        this.targetLanguage = targetLanguage == null || targetLanguage.isBlank() ? "en" : targetLanguage.trim();
    }

    @Override
    public String targetLanguage() {
        return targetLanguage;
    }

    public String translate(String text) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return requestTranslation(text);
            } catch (IOException exception) {
                lastFailure = exception;
                Thread.sleep(500L * attempt);
            }
        }
        throw lastFailure;
    }

    @Override
    public List<String> translateAll(List<String> words) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
        try {
            List<Future<String>> pending = new ArrayList<>();
            for (String word : words) {
                pending.add(pool.submit(() -> translate(word)));
            }
            List<String> translations = new ArrayList<>();
            for (Future<String> translation : pending) {
                translations.add(translation.get());
            }
            return translations;
        } finally {
            pool.shutdownNow();
        }
    }

    private String requestTranslation(String text) throws IOException, InterruptedException {
        String url = API_URL
                + "?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&langpair=ko%7C" + URLEncoder.encode(targetLanguage, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Translation request failed with HTTP " + response.statusCode());
        }

        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        int status = body.has("responseStatus") ? body.get("responseStatus").getAsInt() : 200;
        String translated = body.getAsJsonObject("responseData").get("translatedText").getAsString();
        if (status != 200) {
            // e.g. 429 when the daily free quota is used up; the message explains why.
            throw new IOException("Translation service error " + status + ": " + translated);
        }
        if (translated == null || translated.isBlank()) {
            throw new IOException("No translation returned for \"" + text + "\"");
        }
        // The service sometimes copies stray punctuation (e.g. "Exams:") from its memory.
        return translated.trim().replaceAll("[\\s:;,.]+$", "");
    }
}
