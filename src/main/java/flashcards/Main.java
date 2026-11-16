package flashcards;

import flashcards.view.MainView;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        MainView.launch(MainView.class, args);
    }
}
