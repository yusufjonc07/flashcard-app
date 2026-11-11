package flashcards;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GoogleTranslator {

    private final String targetLanguage;
    private final HttpClient client = HttpClient.newHttpClient();

    public GoogleTranslator() {
        targetLanguage = System.getenv().getOrDefault("GOOGLE_TRANSLATE_TARGET_LANGUAGE", "en");
    }

    public String translate(String text) throws IOException, InterruptedException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to translate must not be blank");
        }

        String query = "client=gtx"
                + "&sl=auto"
                + "&tl=" + encode(targetLanguage)
                + "&dt=t"
                + "&q=" + encode(text);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://translate.google.com/translate_a/single?" + query))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Google Translate request failed (" + response.statusCode()
                    + "): " + response.body());
        }

        String translation = firstTranslation(response.body());
        if (translation == null || translation.isBlank()) {
            throw new IOException("Google Translate response did not contain translated text");
        }
        return translation;
    }

    public Map<String, String> translateAll(List<String> words)
            throws IOException, InterruptedException {
        Map<String, CompletableFuture<String>> requests = new LinkedHashMap<>();
        for (String word : words) {
            requests.putIfAbsent(word, CompletableFuture.supplyAsync(() -> {
                try {
                    return translate(word);
                } catch (IOException | InterruptedException exception) {
                    throw new CompletionException(exception);
                }
            }));
        }

        Map<String, String> translations = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, CompletableFuture<String>> request : requests.entrySet()) {
                translations.put(request.getKey(), request.getValue().join());
            }
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
        return translations;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstTranslation(String response) {
        int start = response.indexOf("[[[\"");
        if (start < 0) {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = start + 4; index < response.length(); index++) {
            char current = response.charAt(index);
            if (escaped) {
                value.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> current;
                });
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return value.toString();
            } else {
                value.append(current);
            }
        }
        return null;
    }
}
