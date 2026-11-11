package flashcards;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class I18nextTranslator implements AutoCloseable {

    private static final String SITE = "https://translate.i18next.com/";
    private final Playwright playwright;
    private final Browser browser;
    private final Page page;
    private final APIRequestContext requests;
    private final String targetLanguage;

    public I18nextTranslator() {
        targetLanguage = System.getenv().getOrDefault("GOOGLE_TRANSLATE_TARGET_LANGUAGE", "en");
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        page.navigate(SITE);
        requests = playwright.request().newContext(new com.microsoft.playwright.APIRequest.NewContextOptions()
                .setUserAgent("Mozilla/5.0")
                .setTimeout(15_000));
    }

    public synchronized Map<String, String> translateAll(List<String> words) throws IOException {
        if (words == null || words.isEmpty()) {
            return Map.of();
        }
        Map<String, String> translations = new LinkedHashMap<>();
        for (String word : words.stream().distinct().toList()) {
            IOException lastFailure = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String url = "https://translate.google.com/translate_a/single"
                            + "?client=gtx&sl=auto&tl=" + encode(targetLanguage)
                            + "&dt=t&q=" + encode(word);
                    var response = requests.get(url);
                    try {
                        if (!response.ok()) {
                            throw new IOException("translation request returned HTTP " + response.status());
                        }
                        String translation = firstTranslation(response.text());
                        if (translation == null || translation.isBlank()) {
                            throw new IOException("translation was empty");
                        }
                        translations.put(word, translation);
                        lastFailure = null;
                        break;
                    } finally {
                        response.dispose();
                    }
                } catch (RuntimeException | IOException exception) {
                    lastFailure = exception instanceof IOException io
                            ? io : new IOException(exception.getMessage(), exception);
                    if (attempt < 3) {
                        try {
                            Thread.sleep(750L * attempt);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Translation interrupted", interrupted);
                        }
                    }
                }
            }
            if (lastFailure != null) {
                throw new IOException("Could not translate \"" + word
                        + "\" through translate.i18next.com after 3 attempts", lastFailure);
            }
        }
        return translations;
    }

    public String translate(String word) throws IOException {
        return translateAll(List.of(word)).get(word);
    }

    @Override
    public void close() {
        requests.dispose();
        browser.close();
        playwright.close();
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
