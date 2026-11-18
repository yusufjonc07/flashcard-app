package flashcards.web;

import flashcards.AppPaths;
import flashcards.model.DocumentSet;
import flashcards.service.DocumentCache;
import flashcards.service.FlashcardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/** Home page (all sets, uploads) and the words page of one set. */
@Controller
public class SetController {

    private static final Path UPLOADS = AppPaths.data().resolve("uploads");
    private static final Set<String> DOCUMENT_TYPES =
            Set.of("pdf", "ppt", "pptx", "png", "jpg", "jpeg", "tif", "tiff");
    private static final Set<String> WORD_LIST_TYPES = Set.of("csv", "txt");

    private final FlashcardService flashcards;
    private final DocumentCache documents;

    public SetController(FlashcardService flashcards, DocumentCache documents) {
        this.flashcards = flashcards;
        this.documents = documents;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("sets", flashcards.overview());
        return "home";
    }

    @PostMapping("/sets")
    public String createSet(@RequestParam String name, RedirectAttributes redirect) {
        DocumentSet set = flashcards.createSet(name);
        redirect.addFlashAttribute("message", "Set \"" + set.name() + "\" is ready. Add your first word.");
        return "redirect:/sets/" + set.id();
    }

    /**
     * One upload form for everything: documents open in the reader, word lists are imported.
     * Files are kept in data/uploads so the reader can open them again later.
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirect)
            throws Exception {
        String name = file.getOriginalFilename() == null ? "" : Path.of(file.getOriginalFilename())
                .getFileName().toString();
        String type = extension(name);
        if (file.isEmpty() || name.isBlank()) {
            throw new IllegalArgumentException("Choose a file to upload");
        }
        if (!DOCUMENT_TYPES.contains(type) && !WORD_LIST_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported file type \"." + type
                    + "\". Use PDF, PPT(X), an image, CSV or TXT.");
        }
        Path saved = save(file, name);

        if (WORD_LIST_TYPES.contains(type)) {
            FlashcardService.ImportResult result = flashcards.importFile(name, saved);
            redirect.addFlashAttribute("message", "Imported " + result.added() + " word(s)"
                    + (result.skipped() > 0 ? ", skipped " + result.skipped() + " already saved" : "") + ".");
            return "redirect:/sets/" + result.set().id();
        }
        DocumentSet set = flashcards.registerDocument(name, saved);
        documents.pages(set);   // read it now so problems are reported straight away
        return "redirect:/sets/" + set.id() + "/read";
    }

    @GetMapping("/sets/{id}")
    public String words(@PathVariable long id, @RequestParam(defaultValue = "") String q, Model model) {
        DocumentSet set = flashcards.set(id);
        model.addAttribute("set", set);
        model.addAttribute("readable", flashcards.canRead(set));
        model.addAttribute("summary", flashcards.summary(id));
        model.addAttribute("cards", flashcards.searchCards(id, q));
        model.addAttribute("q", q);
        return "words";
    }

    @PostMapping("/sets/{id}/words")
    public String addWord(@PathVariable long id, @RequestParam String word,
                          @RequestParam(defaultValue = "") String meaning,
                          RedirectAttributes redirect) throws Exception {
        String saved = flashcards.addWord(id, word, meaning);
        redirect.addFlashAttribute(saved == null ? "error" : "message", saved == null
                ? "\"" + word.trim() + "\" is already in this set."
                : "Added " + word.trim() + " = " + saved);
        return "redirect:/sets/" + id;
    }

    @PostMapping("/sets/{id}/words/{cardId}/delete")
    public String deleteWord(@PathVariable long id, @PathVariable long cardId, RedirectAttributes redirect) {
        flashcards.deleteCard(id, cardId);
        redirect.addFlashAttribute("message", "Word deleted.");
        return "redirect:/sets/" + id;
    }

    private Path save(MultipartFile file, String name) throws IOException {
        Files.createDirectories(UPLOADS);
        Path target = UPLOADS.resolve(name).normalize();
        if (!target.startsWith(UPLOADS)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
