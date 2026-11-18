package flashcards;

import flashcards.model.Card;
import flashcards.service.FlashcardService;
import flashcards.service.Translator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/**
 * The whole application wired by Spring (controllers, templates, service, SQLite), driven
 * through HTTP requests the way the browser does. Uses a temporary database and a fake
 * translator.
 */
@SpringBootTest
class WebFlowTest {

    @TestConfiguration
    static class OfflineTranslator {
        @Bean
        @Primary
        Translator fakeTranslator() {
            Map<String, String> dictionary = Map.of("사과", "apple", "학교", "school");
            return words -> words.stream().map(word -> dictionary.getOrDefault(word, "")).toList();
        }
    }

    @DynamicPropertySource
    static void temporaryDatabase(DynamicPropertyRegistry registry) throws Exception {
        Path db = Files.createTempFile("flashcards-web", ".db");
        db.toFile().deleteOnExit();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    FlashcardService service;
    MockMvc web;

    @BeforeEach
    void setUp() {
        web = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void homePageListsSets() throws Exception {
        service.createSet("Home page set");
        web.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Home page set")));
    }

    @Test
    void addWordWithAutomaticTranslation() throws Exception {
        long set = service.createSet("Words page").id();
        web.perform(post("/sets/" + set + "/words").param("word", "사과").param("meaning", ""))
                .andExpect(redirectedUrlPattern("/sets/" + set + "*"));
        assertEquals("apple", service.allCards(set).get(0).translation());
        web.perform(get("/sets/" + set)).andExpect(status().isOk())
                .andExpect(content().string(containsString("apple")));
    }

    @Test
    void learnPageWrongThenRightAnswer() throws Exception {
        long set = service.createSet("Learn flow").id();
        service.addWord(set, "사과", "apple");
        MockHttpSession session = new MockHttpSession();

        web.perform(get("/sets/" + set + "/learn").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("사과")));
        web.perform(post("/sets/" + set + "/learn/answer").param("answer", "banana").session(session))
                .andExpect(redirectedUrlPattern("/sets/" + set + "/learn*"));
        web.perform(get("/sets/" + set + "/learn").session(session))
                .andExpect(content().string(containsString("apple")));   // correct answer shown
        assertEquals(Card.Status.HIGH_PRIORITY, service.allCards(set).get(0).status());

        web.perform(post("/sets/" + set + "/learn/next").session(session));
        web.perform(post("/sets/" + set + "/learn/answer").param("answer", "Apple").session(session));
        List<Card> cards = service.allCards(set);
        assertEquals(1, cards.get(0).correct());
        assertEquals(1, cards.get(0).streak());
    }

    @Test
    void unknownSetIs404() throws Exception {
        web.perform(get("/sets/999999")).andExpect(status().isNotFound());
    }
}
