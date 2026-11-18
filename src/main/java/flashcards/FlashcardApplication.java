package flashcards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;

/** Starts the web app at http://localhost:8080. */
@SpringBootApplication
public class FlashcardApplication {

    public static void main(String[] args) throws Exception {
        // SQLite creates the database file but not its folder.
        Files.createDirectories(AppPaths.data());
        SpringApplication.run(FlashcardApplication.class, args);
    }
}
