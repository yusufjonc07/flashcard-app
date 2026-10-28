package flashcards;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public final class Main {
    private static final Path DECK_FILE = Path.of("data", "flashcards.csv");
    private static final Path PROGRESS_FILE = Path.of("data", "progress.csv");
    private static final int MASTERY_STREAK = 3;

    private Main() {
    }

    public static void main(String[] args) {
        try {
            FlashcardStore store = new FlashcardStore(DECK_FILE, PROGRESS_FILE);
            List<Flashcard> cards = store.load();
            runMenu(store, cards);
        } catch (IOException exception) {
            System.err.println("Unable to load or save flashcards: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runMenu(FlashcardStore store, List<Flashcard> cards) throws IOException {
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.println("\n=== Flashcard & Mastery Tracker ===");
                System.out.println("1. Start review");
                System.out.println("2. View progress");
                System.out.println("3. Add a flashcard");
                System.out.println("4. Reset progress");
                System.out.println("5. Exit");
                System.out.print("Choose an option: ");

                if (!scanner.hasNextLine()) {
                    store.save(cards);
                    System.out.println("\nProgress saved. Goodbye!");
                    return;
                }
                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1" -> review(scanner, store, cards);
                    case "2" -> showProgress(cards);
                    case "3" -> addCard(scanner, store, cards);
                    case "4" -> resetProgress(scanner, store, cards);
                    case "5" -> {
                        store.save(cards);
                        System.out.println("Progress saved. Goodbye!");
                        return;
                    }
                    default -> System.out.println("Please choose a number from 1 to 5.");
                }
            }
        }
    }

    private static void review(Scanner scanner, FlashcardStore store, List<Flashcard> cards)
            throws IOException {
        List<Flashcard> dueCards = cards.stream()
                .filter(card -> !card.isMastered())
                .sorted(Comparator.comparingInt(Flashcard::priority).reversed()
                        .thenComparing(Flashcard::front, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (dueCards.isEmpty()) {
            System.out.println("\nAll flashcards are mastered. Add new cards or reset progress to review again.");
            return;
        }

        System.out.println("\nType 'q' at any time to end the review session.");
        int reviewed = 0;
        int correct = 0;
        for (Flashcard card : dueCards) {
            System.out.println("\nWord: " + card.front());
            System.out.print("Your answer: ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("q")) {
                break;
            }

            boolean isCorrect = answer.equalsIgnoreCase(card.back());
            card.recordAnswer(isCorrect);
            reviewed++;
            if (isCorrect) {
                correct++;
                System.out.println(card.isMastered()
                        ? "Correct! Mastered after " + MASTERY_STREAK + " consecutive correct answers."
                        : "Correct! Consecutive streak: " + card.consecutiveCorrect());
            } else {
                System.out.println("Not quite. The answer is: " + card.back());
            }
            store.save(cards);
        }

        System.out.printf(Locale.ROOT, "%nSession complete: %d reviewed, %d correct.%n",
                reviewed, correct);
    }

    private static void showProgress(List<Flashcard> cards) {
        long mastered = cards.stream().filter(Flashcard::isMastered).count();
        int total = cards.size();
        System.out.printf("%nProgress: %d/%d mastered (%.0f%%)%n", mastered, total,
                total == 0 ? 0 : mastered * 100.0 / total);
        System.out.printf("%-24s %-24s %8s %8s %8s%n",
                "Front", "Back", "Correct", "Wrong", "Status");
        System.out.println("-".repeat(80));
        cards.stream()
                .sorted(Comparator.comparing(Flashcard::front, String.CASE_INSENSITIVE_ORDER))
                .forEach(card -> System.out.printf("%-24s %-24s %8d %8d %8s%n",
                        truncate(card.front(), 24), truncate(card.back(), 24),
                        card.correct(), card.incorrect(), card.isMastered() ? "Mastered" : "Review"));
    }

    private static void addCard(Scanner scanner, FlashcardStore store, List<Flashcard> cards)
            throws IOException {
        System.out.print("Front (word): ");
        String front = scanner.nextLine().trim();
        System.out.print("Back (answer): ");
        String back = scanner.nextLine().trim();
        if (front.isBlank() || back.isBlank()) {
            System.out.println("Both sides are required.");
            return;
        }
        boolean duplicate = cards.stream().anyMatch(card -> card.front().equalsIgnoreCase(front));
        if (duplicate) {
            System.out.println("A card with that word already exists.");
            return;
        }
        cards.add(new Flashcard(front, back));
        store.save(cards);
        System.out.println("Flashcard added.");
    }

    private static void resetProgress(Scanner scanner, FlashcardStore store, List<Flashcard> cards)
            throws IOException {
        System.out.print("Reset all correct and incorrect answers? (y/N): ");
        if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
            cards.forEach(Flashcard::reset);
            store.save(cards);
            System.out.println("Progress reset.");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private static final class Flashcard {
        private final String front;
        private final String back;
        private int correct;
        private int incorrect;
        private int consecutiveCorrect;
        private boolean mastered;

        Flashcard(String front, String back) {
            this(front, back, 0, 0, 0, false);
        }

        Flashcard(String front, String back, int correct, int incorrect,
                  int consecutiveCorrect, boolean mastered) {
            this.front = front;
            this.back = back;
            this.correct = correct;
            this.incorrect = incorrect;
            this.consecutiveCorrect = consecutiveCorrect;
            this.mastered = mastered;
        }

        String front() {
            return front;
        }

        String back() {
            return back;
        }

        int correct() {
            return correct;
        }

        int incorrect() {
            return incorrect;
        }

        int consecutiveCorrect() {
            return consecutiveCorrect;
        }

        boolean isMastered() {
            return mastered;
        }

        int priority() {
            return incorrect * 3 - correct + (mastered ? -1000 : 0);
        }

        void recordAnswer(boolean isCorrect) {
            if (isCorrect) {
                correct++;
                consecutiveCorrect++;
                mastered = consecutiveCorrect >= MASTERY_STREAK;
            } else {
                incorrect++;
                consecutiveCorrect = 0;
                mastered = false;
            }
        }

        void reset() {
            correct = 0;
            incorrect = 0;
            consecutiveCorrect = 0;
            mastered = false;
        }
    }

    private static final class FlashcardStore {
        private final Path deckFile;
        private final Path progressFile;

        private FlashcardStore(Path deckFile, Path progressFile) {
            this.deckFile = deckFile;
            this.progressFile = progressFile;
        }

        private List<Flashcard> load() throws IOException {
            Files.createDirectories(deckFile.getParent());
            if (!Files.exists(deckFile)) {
                Files.writeString(deckFile, "front,back\napple,사과\nbook,책\nwater,물\n",
                        StandardCharsets.UTF_8);
            }
            List<Flashcard> cards = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(deckFile, StandardCharsets.UTF_8)) {
                String line;
                reader.readLine();
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",", 2);
                    if (values.length == 2 && !values[0].isBlank() && !values[1].isBlank()) {
                        cards.add(new Flashcard(values[0].trim(), values[1].trim()));
                    }
                }
            }
            if (Files.exists(progressFile)) {
                loadProgress(cards);
            }
            return cards;
        }

        private void loadProgress(List<Flashcard> cards) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(progressFile, StandardCharsets.UTF_8)) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",", -1);
                    if (values.length != 5) {
                        continue;
                    }
                    cards.replaceAll(card -> card.front().equals(values[0])
                            ? new Flashcard(card.front(), card.back(), parse(values[1]),
                            parse(values[2]), parse(values[3]), Boolean.parseBoolean(values[4]))
                            : card);
                }
            }
        }

        private int parse(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }

        private void save(List<Flashcard> cards) throws IOException {
            Files.createDirectories(progressFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(progressFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("front,correct,incorrect,streak,mastered\n");
                for (Flashcard card : cards) {
                    writer.write(String.join(",", card.front(), Integer.toString(card.correct()),
                            Integer.toString(card.incorrect()), Integer.toString(card.consecutiveCorrect()),
                            Boolean.toString(card.isMastered())));
                    writer.newLine();
                }
            }
        }
    }
}
