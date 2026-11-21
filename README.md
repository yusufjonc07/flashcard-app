# Document Vocabulary Flashcards

A Spring Boot web application for international students taking lectures in Korean. Upload
the lecture slides (PDF, PPT, image) and read them in the browser. Every Korean word on
the page is translated automatically, and one click saves it as a flashcard. Study with
typed-answer flashcards: words you get wrong come back first, and a word counts as mastered
after **3 consecutive correct answers**. All progress is saved locally in SQLite.

Design and implementation notes: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## 1. Project overview

* **Target user:** A foreign student taking a lecture in Korean.
* **Pain point:** Unfamiliar Korean academic terms get lost in messy notes, and there is no
  way to tell which terms are known and which keep being forgotten.
* **Improvement:** Vocabulary is collected straight from the lecture material and studied as
  flashcards that prioritise the words the student gets wrong, with progress kept between runs.

## 2. Representative scenario

1. The student uploads this week's lecture PDF on the home page. The reader shows each page
   with its Korean words; their meanings are filled in automatically. Clicking **Save** adds
   a word to that document's set.
2. On the next slides, words they already saved are marked **Saved**, so they only look at
   new words.
3. They press **Learn**. The weakest word is shown first. They type the meaning and press
   Enter:
   * A correct answer counts towards mastery (3 in a row).
   * A wrong or blank answer shows the correct meaning, marks the word *High priority*, and
     sends it to the back of the current session.
4. They close the browser or stop the server at any time. Every answer is already saved, so
   the next session starts from the same progress.

## 3. Requirements

| No. | Requirement | Where |
|-----|-------------|-------|
| R1 | Add a Korean term to a vocabulary set | Reader (**Save**), Words page (type it), CSV/TXT upload |
| R2 | Store an English meaning for each term | Translated automatically; can be typed or edited by the user |
| R3 | Give each item a unique ID with term and meaning | `cards` table in SQLite |
| R4 | View all saved words in a numbered list | Words page |
| R5 | Search words by Korean term (or meaning) | Words page search box |
| R6 | Generate flashcards from saved words | Learn page |
| R7 | Reveal the meaning after trying to recall it | Typed-answer feedback, or Know / Don't know |
| R8 | Record whether each answer was correct | Correct/incorrect counters and status per word |
| R9 | Show frequently missed words first | Review order: most mistakes → shortest streak → oldest |
| R10 | Keep words and statistics between runs | Saved to `data/flashcards.db` after every change |

## 4. Acceptance criteria (완료 기준)

Each one is checked automatically by `./gradlew test` (see §8).

### AC1: Correct answer (normal case)
* **Starting state:** "학교" (school) is unmastered with 2 consecutive correct answers.
* **User action:** The user sees "학교", types "School" and presses Enter.
* **Expected result:** The answer is accepted (case-insensitive). "학교" becomes *Mastered*,
  leaves the review queue, and the next word is shown.

### AC2: Incorrect or blank answer (failure case)
* **Starting state:** "사과" (apple) is shown.
* **User action:** The user types "banana", or leaves the field blank, and presses Enter.
* **Expected result:** The program shows the correct answer "apple" and marks "사과"
  *High priority*. The word stays in this session (it comes back after the other words) and
  is at the front of the next session.

### AC3: Saving and restoring (normal case)
* **Starting state:** 5 words have been mastered during the current session.
* **User action:** The user stops the program and starts it again.
* **Expected result:** The 5 words are still *Mastered* and do not appear in the review queue.

## 5. Peer review (상호 검토) and what changed

| Reviewer | Feedback | What was changed |
|----------|----------|------------------|
| KIM YEONHO | Typing the definition of every word by hand is slow; add automatic translation. | Meanings are now translated automatically. The reader translates every word on the page as soon as it opens. On the Words page, leaving the meaning blank translates the word. CSV/TXT lines without a meaning are translated on import. Meanings already saved in any set are reused instead of being translated again, and every translation the service returns is kept in the database, so a page is never translated twice (not even after a restart). The user can still type a meaning, or fix it with **Edit meaning** while learning. |
| KIM TAESONG | When moving to the next slides, show which words were already saved automatically. | The reader checks the set's saved words on every page. Words saved on earlier slides show a green **Saved** button and a highlighted box on the page, and the word list header shows "n saved". The database also refuses the same word twice in one set, so a word can never be saved twice. |

Before the first change, a word's meaning had to be typed in by the user (W09 requirement
R2). Both changes are covered by tests:
* *Peer review (KIM YEONHO): meanings are translated automatically, saved ones reused*
* *A word can be in two sets but only once per set*

The second change was also checked in the running app. In `korean-fairy-tales-105.pdf`, the
9 words saved earlier still show as **Saved** on pages 5 and 7.

## 6. Running the program

**Requirements**
* JDK 25 (tested with Temurin 25.0.4). Gradle is not needed: the `./gradlew` wrapper
  downloads it.
* Internet access:
  * on the first build, to download the libraries from Maven Central;
  * for translation, which uses the free MyMemory API (no key needed).
* For OCR of images on macOS, Tesseract installed with Homebrew (`brew install tesseract`).
  The Korean and English language data is already in `tessdata/`.

```bash
./gradlew bootRun     # start the app, then open http://localhost:8080
./gradlew test        # run the 18 tests (no network needed)
./gradlew bootJar     # build build/libs/flashcard-app-1.0.0.jar
```

`./run.sh` and `./build.sh` do the same as `bootRun` and `bootJar`. Run from the project
folder: `data/` (database and uploads) and `tessdata/` are relative paths.

**Desktop app (macOS).** The same app can be packaged as a normal Mac application with its
own Java runtime, so no JDK is needed to run it. It opens in its own window instead of a browser:

```bash
./gradlew desktopApp   # build/desktop/Flashcards.app  (double-click to start)
./gradlew desktopDmg   # build/desktop/Flashcards-1.0.0.dmg  (installer: drag to Applications)
```

The desktop app keeps its database and uploads in `~/Library/Application Support/Flashcards/`.
The OCR language data is bundled inside the app. Translation still needs internet access, and
OCR of images still needs `brew install tesseract`. Closing the window stops the app.

To translate into a language other than English:

```bash
TRANSLATE_TARGET_LANGUAGE=uz ./gradlew bootRun   # any language code MyMemory supports
```

**Quick demo without a document:** on the home page upload `data/sample-words.csv`, then
press **Learn** on the new set.

## 7. Features

* **Home:**
  * every set with word count, mastered / high-priority counts, accuracy and a mastery bar;
  * one upload form for documents (PDF, PPT, PPTX, PNG, JPG, TIFF) and word lists (CSV, TXT);
  * **New set** creates an empty set for typing words in.
* **Reader:**
  * Each word is translated once; opening a page again uses the saved translations.
  * Shows the page image. Portrait pages are shown two side by side.
  * Clickable boxes on Korean words (PDF) show the meaning on hover.
  * The word list with meanings and **Save** buttons can be toggled with **W**.
  * Words saved earlier are marked **Saved** (peer review).
  * Up to 10 slide thumbnails sit at the bottom of the screen.
  * Keys: ←/→ change page, ↑/↓ select a word, Enter saves it.
* **Learn:**
  * Typed answers. Matching ignores case, extra spaces and punctuation, and a leading
    "a/an/the/to". Any alternative is accepted, e.g. "professor" for "professor, teacher".
  * **Know / Don't know** (→ / ←) for self-grading, **Hint** (H), **Pronounce** (S),
    **Edit meaning** (E).
  * **Undo** of the last answer.
  * At the end: **Start again** or **Shuffle & start**.
* **Words:** numbered list with status and counters, search, add a word (leave the meaning
  blank to translate it), delete.
* **Import:**
  * `.csv` files: `front,back` per line; a header and quoted fields are allowed.
  * `.txt` files: one word per line, optionally followed by a meaning as `word<TAB>meaning`,
    `word - meaning` or `word,meaning`.
  * Words without a meaning are translated.
  * Uploading the same file again skips words already in the set.
* **Errors:**
  * A failed form shows a red message on the same page.
  * An unknown set or card gives a 404 page.
  * The reader shows translation problems in its word list.
  * Nothing is half-saved: each change is one SQL statement or transaction.
* **Background picker** for the reader and learn pages; the choice is remembered in the
  browser.

## 8. Tests

`./gradlew test` runs JUnit 5 tests against a temporary SQLite file and a fake translator:

* `AcceptanceTest` (14 tests):
  * AC1–AC3;
  * answer matching and review order;
  * undo, and a failed save leaving the session unchanged;
  * CSV/TXT import;
  * one word per set;
  * Korean text round-trip;
  * automatic translation and reuse (a page is translated only once);
  * manual sets.
* `WebFlowTest` (4 tests): the whole Spring application driven through HTTP:
  * the home page;
  * adding a word with automatic translation;
  * the learn page (wrong answer, then right answer);
  * 404 for an unknown set.

Results: `build/reports/tests/test/index.html`.

## 9. Project structure

```
src/main/java/flashcards/
  FlashcardApplication.java          Spring Boot entry point
  web/SetController.java             home, upload, words page
  web/ReaderController.java          reader pages and page images
  web/ReaderApiController.java       JSON: page translations, save a word
  web/LearnController.java           learn page (one ReviewSession per browser session)
  web/ErrorHandling.java             error messages for pages and the JSON API
  service/FlashcardService.java      application logic used by all controllers
  service/DocumentCache.java         keeps the last 4 parsed documents in memory
  service/DocumentReader.java        PDF/PPT/image → pages, words, word positions
  service/KoreanWordExtractor.java   Korean tokenising (open-korean-text + korean-nouns.txt)
  service/PDFExtractor.java, PPTExtractor.java, OCRExtractor.java
  service/Translator.java            interface; MyMemoryTranslator.java implements it
  service/CardImporter.java          CSV/TXT parsing
  repository/FlashcardRepository.java  SQLite through JdbcTemplate; schema and migration
  model/Card.java                    flashcard + mastery rule
  model/DocumentSet.java             a vocabulary set
  model/ReviewSession.java           one study session: queue, answer checking, undo
  model/WordPosition.java            word location on a PDF page
src/main/resources/
  application.properties             database file, upload size, session settings
  templates/                         Thymeleaf pages: layout, home, reader, learn, words, error
  static/                            style.css, background.js, backgrounds/, favicon
src/test/java/flashcards/            AcceptanceTest, WebFlowTest
```
