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

The application stores flashcards and review progress in `data/flashcards.db`, using the
system `sqlite3` command. Ensure SQLite is installed and available on `PATH`.

The batch translation behavior mirrors the client-side workflow used by
`translate.i18next.com`: all unique page words are translated concurrently through
its browser workflow. The app uses Playwright's Java library only; no Node.js or
JavaScript project is required. Playwright downloads/launches a Chromium browser
the first time it is used. No API key is required.
The target language defaults to English and can be changed before starting:

```bash
export GOOGLE_TRANSLATE_TARGET_LANGUAGE="en"
```

The OCR runtime also requires Tess4J, JNA, Lept4J, and SLF4J jars in `lib/`, plus
English and Korean Tesseract data files in `tessdata/`. PowerPoint extraction also
requires `SparseBitSet-1.3.jar`, which is included in `lib/`.

The application is a JavaFX desktop app. JavaFX 25 modules for macOS Apple Silicon
are included in `lib/`. On macOS/Linux, run:

```bash
rm -rf out
mkdir out
javac -cp 'lib/*' -d out src/main/java/flashcards/*.java
java \
  --module-path 'lib/javafx-base-25.0.1-mac-aarch64.jar:lib/javafx-graphics-25.0.1-mac-aarch64.jar:lib/javafx-controls-25.0.1-mac-aarch64.jar:lib/javafx-swing-25.0.1-mac-aarch64.jar' \
  --add-modules javafx.controls,javafx.swing \
  --enable-native-access=javafx.graphics \
  --enable-native-access=ALL-UNNAMED \
  -cp 'out:lib/*' \
  flashcards.Main
```

The first launch may download the Playwright Chromium browser. Keep an internet
connection available for this setup and for translation.

The home page shows every document-specific flashcard set with saved-word and
mastery counts. From there, open a document in the reader or start its learning
session. The reader, learning page, and document-set home page are separate UI
screens.

Choose **Open PDF, PPT, or Image** to create or open a document-specific flashcard
set. The reader shows the current page in the center and its extracted vocabulary
in the right sidebar. Click a word to translate and reveal its definition; revealing
it automatically saves that word to the current document's flashcard set. Each
document has its own vocabulary group in SQLite, even when the same word appears in
multiple documents.

The menu supports:

* weakest-first review, with three consecutive correct answers required for mastery;
* manual word entry with Google translation;
* importing `.csv` files containing `front,back`, `.txt`, `.pdf`, `.ppt`, `.pptx`,
  `.png`, `.jpg`, `.jpeg`, `.tif`, and `.tiff` files;
* progress summaries.

Answers are compared case-insensitively. Database failures and translation failures
are displayed instead of silently creating incomplete cards.


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