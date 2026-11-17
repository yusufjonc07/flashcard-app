# 개발 문서: Document Vocabulary Flashcards

Java 2026-2 개인 프로젝트 · 개발 문서 (W09 기획 → W10 설계 → W11 구현·시험)

Requirements and acceptance criteria are in [README.md](../README.md) §3–4. This document
covers the design, the flow of the representative scenario, the implementation order, the
design changes made along the way, and the test results.

---

## 1. 구성요소와 책임 (Components and responsibilities)

| Component | Class | Responsibility | Requirements |
|-----------|-------|----------------|--------------|
| Screen | `view.MainView` | Shows the home, reader, learn and words pages; turns clicks and key presses into controller calls; shows results and errors. Holds no business rules. | all |
| Coordinator | `controller.AppController` | The only entry point for the view. Combines storage, translation and reading; builds summaries; starts review sessions. | all |
| Flashcard | `model.Card` | One word and its progress. Owns the **mastery rule** (`answered()`) and the status (New / High priority / Learning / Mastered). | R3, R8 |
| Study session | `model.ReviewSession` | The queue of cards for one session, answer checking, re-queuing wrong cards, undo. | R6, R7, R8, R9 |
| Vocabulary set | `model.DocumentSet` | A named group of cards: from a document, an imported file, or created empty. | R1 |
| Storage | `model.SQLiteStore` | Saves and restores sets and cards in `data/flashcards.db`; review order (weakest first). | R3, R9, R10 |
| Document reading | `service.DocumentReader` (+ `PDFExtractor`, `PPTExtractor`, `OCRExtractor`, `KoreanWordExtractor`) | Turns a PDF/PPT/image into pages with images, text, Korean words and word positions. | R1 |
| Translation | `service.Translator` (interface), `service.MyMemoryTranslator` | Korean → English (or another language), with retries and parallel requests. | R2 |
| Import | `service.CardImporter` | Parses CSV/TXT word lists. | R1, R2 |

### Class state and behaviour (상태와 동작)

| Class | State | Behaviour |
|-------|-------|-----------|
| `Card` (immutable record) | id, set id, front, translation, correct, incorrect, streak, mastered | `answered(correct)` → next state; `status()`, `statusText()` |
| `ReviewSession` | queue of cards, total, answered / correct / newly-mastered counters, one undo snapshot | `current()`, `submit(text)`, `grade(knew)`, `undo()`, `matches(typed, translation)` |
| `SQLiteStore` | database file path | `addDocument`, `documents`, `addCard(s)`, `allCards`, `reviewCards`, `card`, `saveProgress`, `deleteCard`, `knownTranslations` |
| `AppController` | store, translator | `registerDocument`, `createSet`, `readDocument`, `addWord`, `saveCard`, `deleteCard`, `importFile`, `searchCards`, `summary`, `startReview`, `translateAll` |

### Checks from the lecture (확인)

* **Is any behaviour missing or duplicated?** The mastery rule used to live in two places:
  SQL in `recordAnswer` and a copied card for undo. It now exists only in
  `Card.answered()`. The store only writes the result (`saveProgress`), and undo writes back
  the saved earlier state.
* **Who changes state?**
  * Card progress: computed by `Card`, triggered by `ReviewSession`, written by `SQLiteStore`.
  * Session queue: only `ReviewSession`.
  * Sets and words: only `SQLiteStore`, always through `AppController`.
  * The view never changes data directly.

## 2. 구성요소 간 협력 (Collaboration)

```
            ┌────────────────────────────── AppController ─────────────────────────────┐
MainView ──▶│ readDocument ─▶ DocumentReader ─▶ PDF/PPT/OCR extractors ─▶ KoreanWordExtractor
 (screens)  │ translateAll ─▶ SQLiteStore.knownTranslations ─▶ Translator (MyMemory, only missing words)
            │ importFile   ─▶ CardImporter ─▶ translateAll ─▶ SQLiteStore.addCards
            │ startReview  ─▶ SQLiteStore.reviewCards ─▶ new ReviewSession(cards, store::saveProgress)
            └──────────────────────────────────────────────────────────────────────────┘
MainView ──▶ ReviewSession.submit/grade/undo ─▶ Card.answered ─▶ (ProgressSaver) SQLiteStore.saveProgress ─▶ flashcards.db
```

Why it is split this way:

* **View → controller only.** Screens can change without touching storage, and everything
  except the GUI can be tested without JavaFX.
* **`ReviewSession` gets a `ProgressSaver` instead of the store.** It has no database
  code, so tests can pass a fake saver (for example one that fails), and the session can be
  used outside the GUI.
* **`Translator` is an interface.** Tests use a fake dictionary, so they run offline and do
  not use up the free translation quota.
* **Translation goes through the controller.** Words already saved in any set are reused, so
  only new words are sent to the translation service.

## 3. 대표 시나리오의 흐름 (Representative scenario flow)

**Starting state:** set "2026-2 Java 11주차.pdf" contains
* 학교 = school: 2 correct in a row, unmastered
* 사과 = apple: 1 mistake (High priority)
* 도서관 = library: New

**Inputs:** in one session the user types `banana` for 사과, then `Library` for 도서관, then
`School` for 학교, then `apple` for 사과.

| Step | Request | Data passed | State change (where) | Result |
|------|---------|-------------|----------------------|--------|
| 1 | Learn → `controller.startReview(setId)` | set id | — | `reviewCards` returns 사과 (1 mistake), 도서관 (streak 0), 학교 (streak 2) |
| 2 | Enter → `session.submit("banana")` | typed text | `Card.answered(false)` → incorrect 2, streak 0; `saveProgress` writes it; session moves 사과 to the back | Red: "Correct answer: apple", badge High priority |
| 3 | `submit("Library")` | typed text | 도서관: correct 1, streak 1 (DB); removed from session | Green, "Learning (1/3)" |
| 4 | `submit("School")` | typed text | 학교: streak 3 → **mastered** (DB); removed from session | "학교 is now mastered!" |
| 5 | `submit("apple")` | typed text | 사과: streak 1 (DB); session empty | "Session complete: 4 answers, 3 correct, 1 newly mastered" |
| 6 | Close and reopen the app | — | none: every answer was already saved | 학교 shows as Mastered on the home page and is not in the queue |

**When a request is rejected, what stays the same:**
* *Blank or wrong answer:* this counts as an answer, not an error. The streak resets and
  nothing else changes.
* *Database error while saving:* `ReviewSession` saves **before** changing its queue, so the
  card stays current and the counters do not change. The view shows the error and the user
  can try again. The test `a failed save leaves the session unchanged` checks this.
* *Duplicate word:* `addCards` skips words already in the set, inside one transaction, and
  reports how many were skipped. Existing progress is kept.
* *Translation failure:* nothing is saved, and a dialog tells the user to type the meaning.

## 4. 구현 계획과 순서 (Implementation order)

1. ✅ Read a document and list its Korean words (PDF → PPT → OCR).
2. ✅ Translate words and save a revealed word to the document's set (SQLite).
3. ✅ Home page with one set per document, and a basic learn page.
4. ✅ Representative scenario end to end: typed answer → check → save → next card
   (`ReviewSession`), with AC1 and AC2.
5. ✅ Restore after restart (AC3). Every answer is written immediately instead of on exit,
   so a crash or a force-quit loses nothing.
6. ✅ Extra features: words page (numbered list, search, add, delete), CSV/TXT import, empty
   sets, summaries, undo, translation cache and retries.
7. ✅ Automated acceptance tests (`./test.sh`).
8. ⏭ Next: peer-review fixes, presentation, demo rehearsal.

### Storage (저장과 복원)

`data/flashcards.db` (SQLite):

```
documents(id, name, path UNIQUE, created_at)          -- one row per set
cards(id, document_id → documents.id, front, translation,
      correct, incorrect, streak, mastered, created_at,
      UNIQUE(document_id, front))                       -- same word may be in several sets
```

* Files are opened with the `sqlite3` command and text is read back as hex, so Korean text,
  quotes and tabs survive unchanged. No JDBC driver is needed.
* Sets from documents use the document's absolute path as their key, so opening the same PDF
  again reuses its set.
* Imported lists use the file's path. Empty sets use `manual:<name>`.
* Older databases are migrated automatically on start. These are databases where `front` was
  unique across all sets or where `document_id` was missing.

## 5. 설계 변경과 이유 (Design changes and reasons)

| # | Change | Reason |
|---|--------|--------|
| 1 | Console program → JavaFX desktop app | The main pain point is lecture slides. Words are easier to collect while looking at the slide, and clicking a word is faster than typing it. |
| 2 | CSV/TXT file → SQLite | Progress is saved after every answer, with no risk of losing a half-written file, and adding per-document sets was easy. CSV/TXT is still supported as an import format. |
| 3 | One global word list → one set per document | A word can mean different things in different lectures, and students study one lecture at a time. |
| 4 | Mastery: open question → **3 correct in a row** | One correct answer can be a lucky guess. The W09 open question "how many correct answers for mastery?" is resolved here. AC1 was rewritten with streak 2 as its starting state so the criterion matches. |
| 5 | Self-graded "I know / I don't know" → **typed answer** plus optional "Show answer" | AC1 and AC2 require typed answers, case-insensitive matching, and blank = wrong. Self-grading is kept as a fallback for long meanings. |
| 6 | Wrong cards moved on → **re-queued in the same session** | AC2 says the word stays in the active rotation. |
| 7 | Mastery rule in SQL and in the view's undo → only in `Card.answered()` | The rule was duplicated (see §1). Undo now restores an exact snapshot. |
| 8 | Translator class → `Translator` interface + MyMemory implementation + cache | Allows offline tests, saves quota, and allows a configurable target language. |

## 6. 시험 결과 (Test results)

`./test.sh` builds everything into `out-test/` and runs `AcceptanceTests`. Each test uses a
new temporary database and a fake translator.

```
PASS  R1  correct typed answer masters a card on its 3rd consecutive success
PASS  R1  answers match case-insensitively and accept listed alternatives
PASS  R2  wrong answer marks high priority, shows answer, stays in rotation
PASS  R2  blank answer counts as incorrect
PASS  R3  mastered words survive restarting the program
PASS       review queue puts the weakest words first
PASS       undo restores the database and the session queue
PASS       a failed save leaves the session unchanged
PASS       CSV import: header, quotes, duplicates, auto-translation
PASS       TXT import: separators and words without meaning
PASS       same word may exist in two sets; not twice in one set
PASS       Korean text and quotes round-trip through SQLite
PASS       saved translations are reused instead of re-translated
PASS       manual set: add word, search, summary, delete

14 passed, 0 failed
```

(R1–R3 here are the three acceptance criteria AC1–AC3 in the README.)

Manual checks (2026-11-16):
* The app starts with no errors.
* Sets in the existing database load with correct counts.
* Live MyMemory translation works (도서관 → Library, 시험 → Exams).

## 7. Peer review 기록

| Date | Reviewer | Feedback | What was changed / why (or why not) |
|------|----------|----------|-------------------------------------|
| 11/04 | | | |
| 11/18 | | | |

## 8. Known limitations and open questions

* The bundled JavaFX jars are for macOS Apple Silicon. Other platforms need the matching
  JavaFX jars in `lib/` and in `run.sh`.
* MyMemory's free tier allows about 5,000 characters per day per IP address. When the limit
  is reached, the error is shown and words can still be added by typing the meaning.
* Clickable word boxes on the page image exist only for PDFs. For PPT and image files, the
  words are still listed in the sidebar.
* Single-word translation has no context, so some meanings are approximate. The user can
  delete the word and add it again with their own meaning.
* Open question: should mastered words come back after a few days (spaced repetition)?
