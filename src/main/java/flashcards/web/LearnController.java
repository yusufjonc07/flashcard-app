package flashcards.web;

import flashcards.model.DocumentSet;
import flashcards.model.ReviewSession;
import flashcards.service.FlashcardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

/**
 * The flashcard page. Each browser session keeps one {@link ReviewSession} per set in the
 * HTTP session. Every button posts and then redirects back (post/redirect/get), so
 * refreshing the page never submits an answer twice.
 */
@Controller
@RequestMapping("/sets/{id}/learn")
public class LearnController {

    /** What the learn page shows: the session, the last answer's result, and reveal state. */
    public static final class LearningState {
        private final ReviewSession session;
        private ReviewSession.AnswerResult lastResult;
        private boolean revealed;

        LearningState(ReviewSession session) {
            this.session = session;
        }

        public ReviewSession getSession() {
            return session;
        }

        public ReviewSession.AnswerResult getLastResult() {
            return lastResult;
        }

        public boolean isRevealed() {
            return revealed;
        }

        public int getPercentDone() {
            return session.total() == 0 ? 100
                    : Math.round(100f * (session.total() - session.remaining()) / session.total());
        }
    }

    private static final String SESSION_KEY = "learningStates";

    private final FlashcardService flashcards;

    public LearnController(FlashcardService flashcards) {
        this.flashcards = flashcards;
    }

    @GetMapping
    public String show(@PathVariable long id, HttpSession http, Model model) {
        DocumentSet set = flashcards.set(id);
        model.addAttribute("set", set);
        model.addAttribute("state", state(id, http));
        return "learn";
    }

    /** Checks a typed answer (R1, R2). Pressing Enter on the result moves to the next card. */
    @PostMapping("/answer")
    public String answer(@PathVariable long id, @RequestParam(defaultValue = "") String answer,
                         HttpSession http) throws Exception {
        LearningState state = state(id, http);
        if (state.lastResult == null && !state.session.isFinished()) {
            state.lastResult = state.session.submit(answer);
            state.revealed = false;
        }
        return redirect(id);
    }

    /** "Show answer": reveal the meaning and let the user grade themselves (R7). */
    @PostMapping("/reveal")
    public String reveal(@PathVariable long id, HttpSession http) {
        LearningState state = state(id, http);
        if (state.lastResult == null && !state.session.isFinished()) {
            state.revealed = true;
        }
        return redirect(id);
    }

    @PostMapping("/grade")
    public String grade(@PathVariable long id, @RequestParam boolean knew, HttpSession http) throws Exception {
        LearningState state = state(id, http);
        if (state.revealed && state.lastResult == null && !state.session.isFinished()) {
            state.lastResult = state.session.grade(knew);
            state.revealed = false;
        }
        return redirect(id);
    }

    @PostMapping("/next")
    public String next(@PathVariable long id, HttpSession http) {
        state(id, http).lastResult = null;
        return redirect(id);
    }

    @PostMapping("/undo")
    public String undo(@PathVariable long id, HttpSession http) throws Exception {
        LearningState state = state(id, http);
        if (state.session.undo()) {
            state.lastResult = null;
            state.revealed = false;
        }
        return redirect(id);
    }

    /** Starts a fresh session with the current weakest-first queue. */
    @PostMapping("/restart")
    public String restart(@PathVariable long id, HttpSession http) {
        states(http).remove(id);
        return redirect(id);
    }

    private LearningState state(long id, HttpSession http) {
        return states(http).computeIfAbsent(id, setId -> new LearningState(flashcards.startReview(setId)));
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, LearningState> states(HttpSession http) {
        Map<Long, LearningState> states = (Map<Long, LearningState>) http.getAttribute(SESSION_KEY);
        if (states == null) {
            states = new HashMap<>();
            http.setAttribute(SESSION_KEY, states);
        }
        return states;
    }

    private static String redirect(long id) {
        return "redirect:/sets/" + id + "/learn";
    }
}
