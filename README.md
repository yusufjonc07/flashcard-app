# Development Document: Console Flashcard & Mastery Tracker

## 1. Project Overview
* **Project Name:** Console Flashcard & Mastery Tracker[cite: 12]
* **Target User:** Students or self-learners who need to memorize a large volume of vocabulary[cite: 12].
* **Problem to Solve (Pain Point):** Relying on physical flashcards or simple text lists makes it difficult to track which words have been mastered and which need more review. Users waste time restudying known material[cite: 12].
* **Expected Improvement:** A local console program that tracks the success rate of each flashcard, prioritizes testing the weakest words, and saves study progress locally to optimize the next session[cite: 12].

## 2. Representative Usage Scenario
* **Situation & Purpose:** A student opens the program for their daily study session to review vocabulary efficiently[cite: 12].
* **User Actions and Program Reactions:**[cite: 12]
    1. The user selects "Start Review" from the main console menu.
    2. The program loads the saved vocabulary data and displays a word prioritized for review.
    3. The user types the answer. 
    4. If correct, the program updates the word's status to "mastered." If incorrect, the program marks it for "high priority review."
* **Desired Result After Use:** When the user selects "Exit," the program saves the updated statistics and word states to a local file, ensuring the next execution begins with accurate progress data[cite: 12].

## 3. Items to Confirm (Questions)
* How many consecutive correct answers are required to permanently mark a word as "mastered"?[cite: 12]
* Should the initial flashcard deck be loaded from a `.txt` or `.csv` file?[cite: 12]

## 4. Running the Java application

The implementation uses a CSV deck in `data/flashcards.csv` and stores statistics in
`data/progress.csv`. If the deck does not exist, the application creates a small starter
deck automatically.

```bash
javac -d out src/main/java/flashcards/Main.java
java -cp out flashcards.Main
```

The default behavior requires three consecutive correct answers to mark a card as
mastered. The menu supports weakest-first review, progress viewing, adding cards, and
resetting progress. Answers are compared case-insensitively.


## 5. Acceptance Criteria (완료 기준)

### Requirement 1: Answering a Flashcard Correctly (Normal Case)
* **Starting State:** The vocabulary word "학교" (school) is in the "Needs Review" queue.
* **User Action:** The user sees "학교" and types the correct translation, "school".
* **Expected Result:** The program updates the status of "학교" to "Mastered," removes it from the immediate review queue, and displays the next word.

### Requirement 2: Answering a Flashcard Incorrectly (Failure / Edge Case)
* **Starting State:** The vocabulary word "사과" (apple) is displayed.
* **User Action:** The user types an incorrect answer (e.g., "banana") or leaves the input blank.
* **Expected Result:** The program marks "사과" as "High Priority," displays the correct answer to the user, and keeps it in the active review rotation.

### Requirement 3: Saving and Restoring Data (Normal Case)
* **Starting State:** The user has correctly answered 5 words during the current session, changing their status to "Mastered".
* **User Action:** The user selects "Exit Program" from the main menu.
* **Expected Result:** The program writes the newly updated statuses to the local data file before closing. Upon the next execution, those 5 words remain marked as "Mastered" and do not appear in the immediate review queue.