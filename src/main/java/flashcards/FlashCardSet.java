package flashcards;

public class FlashCardSet {
    String title;

    class FlashCard {
        String word;
        String definition;
        boolean isMastered = false;
        int numOfCorrect = 0;
    }
}
