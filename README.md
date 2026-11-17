# Document Vocabulary Flashcards

A JavaFX desktop app for international students taking lectures in Korean. Open lecture
slides (PDF, PPT, image), click unfamiliar words to translate them and save them as
flashcards, then study with typed-answer flashcards. Words you get wrong come back first;
a word counts as mastered after **3 consecutive correct answers**. All progress is saved
locally in SQLite.

Design and implementation notes: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## 1. Project overview

* **Target user:** A foreign student taking a lecture in Korean.
* **Pain point:** Unfamiliar Korean academic terms get lost in messy notes, and there is no
  way to tell which terms are known and which keep being forgotten.
* **Improvement:** Vocabulary is collected straight from the lecture material and studied as
  flashcards that prioritise the words the student gets wrong, with progress kept between runs.

## 2. Representative scenario

1. The student opens this week's lecture PDF. The reader shows each page with its Korean
   words in a sidebar; clicking a word translates it and saves it to that document's set.
2. On the home page they press **Learn** on the set. The weakest word is shown first.
3. They type the meaning and press Enter. Correct answers count towards mastery (3 in a row);
   wrong or blank answers show the correct meaning, mark the word *High priority* and send it
   to the back of the current session.
4. They close the program at any time. Every answer is already saved, so the next session
   starts from the same progress.

## 3. Requirements

| No. | Requirement | Where |
|-----|-------------|-------|
| R1 | Add a Korean term to a vocabulary set | Reader (click a word), Words page (type it), CSV/TXT import |
| R2 | Store an English meaning for each term | Automatic translation, or typed by the user |
| R3 | Give each item a unique ID with term and meaning | `cards` table in SQLite |
| R4 | View all saved words in a numbered list | Words page |
| R5 | Search words by Korean term (or meaning) | Words page search box |
| R6 | Generate flashcards from saved words | Learn page |
| R7 | Reveal the meaning after trying to recall it | "Show answer" / typed-answer feedback |
| R8 | Record whether each answer was correct | Correct/incorrect counters and status per word |
| R9 | Show frequently missed words first | Review order: most mistakes → shortest streak → oldest |
| R10 | Keep words and statistics between runs | Saved to `data/flashcards.db` after every change |

## 4. Acceptance criteria (완료 기준)

Each one is checked automatically by `./test.sh` (see §6).

### AC1: Correct answer (normal case)
* **Starting state:** "학교" (school) is unmastered with 2 consecutive correct answers.
* **User action:** The user sees "학교", types "School" and presses Enter.
* **Expected result:** The answer is accepted (case-insensitive). "학교" becomes *Mastered*,
  leaves the review queue, and the next word is shown.

### AC2: Incorrect or blank answer (failure case)
* **Starting state:** "사과" (apple) is shown.
* **User action:** The user types "banana", or leaves the field blank, and presses Enter.
* **Expected result:** The program shows the correct answer "apple", marks "사과"
  *High priority*, and keeps it in this session (it comes back after the other words) and at
  the front of the next session.

### AC3: Saving and restoring (normal case)
* **Starting state:** 5 words have been mastered during the current session.
* **User action:** The user closes the program and starts it again.
* **Expected result:** The 5 words are still *Mastered* and do not appear in the review queue.

## 5. Running the program

**Requirements**
* JDK 25 (tested with Temurin 25.0.4)
* `sqlite3` on the `PATH` (preinstalled on macOS)
* The jars in `lib/` (JavaFX 25 for macOS Apple Silicon, PDFBox, Apache POI, Tess4J,
  open-korean-text, Gson). They are not in git because of their size (~275 MB) and are
  shipped with the submission.
* Internet access for translation. Translation uses the free MyMemory API; no key is needed.

```bash
./build.sh     # compile into out/
./run.sh       # start the app
./test.sh      # run the acceptance tests (no GUI or network needed)
```

Run the scripts from the project folder, because `data/` and `tessdata/` are relative paths.

To translate into a language other than English, set it before starting:

```bash
export TRANSLATE_TARGET_LANGUAGE=uz   # any ISO code MyMemory supports
```

**Quick demo without a document:** on the home page choose **Import CSV / TXT** and pick
`data/sample-words.csv`, then press **Learn** on the new set.

## 6. Features

* **Home page:** every set with word count, mastered / high-priority counts, accuracy and
  a mastery bar. Buttons: *Open reader*, *Words*, *Learn*.
* **Reader:** PDF, PPTX, PPT, PNG/JPG/TIFF (OCR with Tesseract, Korean + English). Page
  thumbnails, zoom, ←/→ for pages, ↑/↓ and Enter to pick a word. Hover a word to see its
  meaning; click it to save it to the set.
* **Learn:** typed answers with case-insensitive matching that ignores punctuation and a
  leading "a/an/the/to", and accepts any alternative in "professor, teacher". *Show answer*
  lets the user grade themselves instead. *Undo* reverts the last answer.
* **Words page:** numbered list with status and counters, search, add a word (leave the
  meaning blank to translate it), delete.
* **Import:** `.csv` with `front,back` (header and quoted fields allowed) or `.txt` with one
  word per line, optionally `word<TAB>meaning`, `word - meaning` or `word,meaning`. Words
  without a meaning are translated. Importing the same file again skips existing words.
* **Errors:** database, file and translation errors are shown in a dialog; no half-made
  cards are saved.

## 7. Project structure (MVC)

```
src/main/java/flashcards/
  Main.java                    launcher
  view/MainView.java           JavaFX screens: home, reader, learn, words
  controller/AppController.java  the only entry point the view uses
  model/Card.java              flashcard + mastery rule
  model/DocumentSet.java       a vocabulary set
  model/ReviewSession.java     one study session: queue, answer checking, undo
  model/SQLiteStore.java       persistence (sqlite3 process)
  model/WordPosition.java      word location on a PDF page
  service/DocumentReader.java  PDF/PPT/image → pages + words
  service/KoreanWordExtractor.java  Korean tokenising (open-korean-text)
  service/PDFExtractor.java, PPTExtractor.java, OCRExtractor.java
  service/Translator.java      interface; MyMemoryTranslator.java is the implementation
  service/CardImporter.java    CSV/TXT parsing
src/test/java/flashcards/AcceptanceTests.java
```
