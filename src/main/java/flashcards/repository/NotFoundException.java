package flashcards.repository;

/** A set or card id that does not exist; shown to the user as "404 Not found". */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
