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