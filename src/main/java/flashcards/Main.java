package flashcards;

public class Main {
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String CYAN = "\u001B[36m";
    static final String RESET = "\u001B[0m";

    public static void main(String[] args) {
        System.out.println(CYAN + "=== Korean Vocabulary Trainer ===" + RESET);
        System.out.println(GREEN + "1. Start studying" + RESET);
        System.out.println(GREEN + "2. Import CSV" + RESET);
        System.out.println(RED + "3. Exit" + RESET);
    }
}