package flashcards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the web app at http://localhost:8080. */
@SpringBootApplication
public class FlashcardApplication {

    public static void main(String[] args) throws Exception {
        // SQLite creates the database file but not its folder.
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(FlashcardApplication.class, args);
    }
}
