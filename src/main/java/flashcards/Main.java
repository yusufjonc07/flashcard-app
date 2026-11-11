package flashcards;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.image.ImageView;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main extends Application {

    private final VBox wordsSidebar = new VBox(8);
    private final TextArea reader = new TextArea();
    private final ImageView pageImage = new ImageView();
    private final StackPane pageView = new StackPane();
    private final ScrollPane documentScroll = new ScrollPane();
    private final List<Button> vocabularyButtons = new ArrayList<>();
    private final Label pageLabel = new Label();
    private final Label status = new Label("Open a document to begin.");
    private SQLiteStore store;
    private I18nextTranslator translator;
    private List<DocumentReader.Page> pages = List.of();
    private int pageIndex;
    private long documentId;
    private Map<String, String> pageTranslations = Map.of();
    private Stage stage;
    private Scene scene;
    private BorderPane readerRoot;
    private double zoom = 1.0;
    private int vocabularyFocus = -1;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        try {
            store = new SQLiteStore("data/flashcards.db");
            translator = new I18nextTranslator();
        } catch (Exception exception) {
            error("Database error", exception);
            return;
        }

        readerRoot = new BorderPane();
        readerRoot.setPadding(new Insets(18));
        readerRoot.setTop(header(stage));
        readerRoot.setCenter(readerPanel());
        readerRoot.setRight(sidebar());
        stage.setTitle("Document Vocabulary Flashcards");
        scene = new Scene(homePage(), 1150, 720);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleReaderKey);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    private BorderPane homePage() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(28));
        Label title = new Label("Vocabulary Studio");
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold;");
        Label subtitle = new Label("Read documents, collect vocabulary, and build your memory.");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: #6b7280;");
        Button open = new Button("Open a document");
        open.setOnAction(event -> chooseDocument(stage));
        Button learn = new Button("Start learning");
        learn.setOnAction(event -> showLearningPage());
        HBox actions = new HBox(10, open, learn);
        VBox header = new VBox(8, title, subtitle, actions);
        header.setPadding(new Insets(0, 0, 24, 0));

        VBox sets = new VBox(12);
        try {
            List<SQLiteStore.Document> documents = store.documents();
            if (documents.isEmpty()) {
                sets.getChildren().add(new Label("No document sets yet. Open a PDF, PPT, or image to begin."));
            } else {
                for (SQLiteStore.Document document : documents) {
                    List<SQLiteStore.Card> cards = store.allCards(document.id());
                    long mastered = cards.stream().filter(SQLiteStore.Card::mastered).count();
                    Label name = new Label(document.name());
                    name.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
                    Label stats = new Label(cards.size() + " saved words  •  " + mastered + " mastered");
                    stats.setStyle("-fx-text-fill: #6b7280;");
                    Button readerButton = new Button("Open reader");
                    readerButton.setOnAction(event -> loadDocument(Path.of(document.path())));
                    Button learnButton = new Button("Learn set");
                    learnButton.setOnAction(event -> showLearningPage(document));
                    HBox row = new HBox(12, new VBox(4, name, stats), readerButton, learnButton);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(16));
                    row.setStyle("-fx-background-color: white; -fx-border-color: #e5e7eb; "
                            + "-fx-background-radius: 8; -fx-border-radius: 8;");
                    HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
                    sets.getChildren().add(row);
                }
            }
        } catch (Exception exception) {
            error("Could not load document sets", exception);
        }
        ScrollPane scroll = new ScrollPane(sets);
        scroll.setFitToWidth(true);
        root.setTop(header);
        root.setCenter(scroll);
        return root;
    }

    @Override
    public void stop() {
        if (translator != null) {
            translator.close();
        }
    }

    private VBox header(Stage stage) {
        Label title = new Label("Document Vocabulary Reader");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Button open = new Button("Open PDF, PPT, or Image");
        open.setOnAction(event -> chooseDocument(stage));
        Button learn = new Button("Learn flashcards");
        learn.setOnAction(event -> showLearningPage());
        Button previous = new Button("Previous page");
        previous.setOnAction(event -> showPage(pageIndex - 1));
        Button next = new Button("Next page");
        next.setOnAction(event -> showPage(pageIndex + 1));
        Button zoomOut = new Button("−");
        zoomOut.setOnAction(event -> changeZoom(-0.1));
        Button zoomReset = new Button("100%");
        zoomReset.setOnAction(event -> resetZoom());
        Button zoomIn = new Button("+");
        zoomIn.setOnAction(event -> changeZoom(0.1));
        HBox zoomControls = new HBox(4, zoomOut, zoomReset, zoomIn);
        HBox controls = new HBox(8, open, learn, previous, next, pageLabel,
                new Separator(), new Label("Zoom"), zoomControls);
        controls.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10, title, controls, status);
    }

    private void showLearningPage() {
        showLearningPage(null);
    }

    private void showLearningPage(SQLiteStore.Document initialDocument) {
        ComboBox<SQLiteStore.Document> documents = new ComboBox<>();
        try {
            documents.getItems().setAll(store.documents());
        } catch (Exception exception) {
            error("Could not load document sets", exception);
            return;
        }
        documents.setPromptText("Choose a document set");
        documents.setCellFactory(list -> documentCell());
        documents.setButtonCell(documentCell());
        if (initialDocument != null) {
            documents.setValue(initialDocument);
        }

        VBox cardArea = new VBox(22);
        cardArea.setAlignment(Pos.CENTER);
        Label mode = new Label("FLASHCARD MODE");
        mode.setStyle("-fx-text-fill: #8b5cf6; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label prompt = new Label("Choose a document set to start learning.");
        prompt.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: #172033;");
        Label answer = new Label();
        answer.setStyle("-fx-font-size: 24px; -fx-text-fill: #6d28d9; -fx-font-weight: bold;");
        answer.setVisible(false);
        Label progressLabel = new Label();
        Button reveal = new Button("Show answer");
        reveal.setStyle("-fx-background-color: #6d28d9; -fx-text-fill: white; -fx-font-weight: bold;");
        Button correct = new Button("→  I know");
        Button incorrect = new Button("←  I don't know");
        Button undo = new Button("↶  Undo");
        Button back = new Button("‹  Back to sets");
        correct.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;");
        incorrect.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
        undo.setStyle("-fx-background-color: transparent; -fx-text-fill: #6b7280;");
        HBox grading = new HBox(14, incorrect, correct);
        grading.setAlignment(Pos.CENTER);
        grading.setVisible(false);
        cardArea.getChildren().addAll(mode, prompt, answer, reveal, grading, undo, progressLabel);
        cardArea.setPadding(new Insets(36));
        cardArea.setStyle("-fx-background-color: white; -fx-background-radius: 18; "
                + "-fx-border-color: #e5e7eb; -fx-border-radius: 18;");

        class CardsHolder {
            private List<SQLiteStore.Card> value = List.of();
        }
        CardsHolder cards = new CardsHolder();
        final int[] index = {0};
        final SQLiteStore.Card[] lastCard = {null};
        Runnable loadCard = () -> {
            try {
                SQLiteStore.Document selected = documents.getValue();
                cards.value = store.reviewCards(selected == null ? null : selected.id());
                index[0] = 0;
                lastCard[0] = null;
                undo.setDisable(true);
                showLearningCard(cards.value, index, prompt, answer, reveal, grading, progressLabel);
            } catch (Exception exception) {
                error("Could not load learning cards", exception);
            }
        };
        documents.setOnAction(event -> loadCard.run());
        reveal.setOnAction(event -> {
            if (index[0] < cards.value.size()) {
                answer.setVisible(true);
                reveal.setDisable(true);
                grading.setVisible(true);
            }
        });
        incorrect.setOnAction(event -> gradeCard(false, cards.value, index, lastCard, undo, prompt, answer,
                reveal, grading, progressLabel));
        correct.setOnAction(event -> gradeCard(true, cards.value, index, lastCard, undo, prompt, answer,
                reveal, grading, progressLabel));
        undo.setOnAction(event -> undoCard(cards.value, index, lastCard, undo, prompt, answer,
                reveal, grading, progressLabel));
        back.setOnAction(event -> scene.setRoot(homePage()));

        Label title = new Label("Learn");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #172033;");
        HBox top = new HBox(18, back, title, new Label("Set:"), documents);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 24, 0));
        BorderPane learningRoot = new BorderPane();
        learningRoot.setPadding(new Insets(28));
        learningRoot.setStyle("-fx-background-color: #f7f7fb;");
        learningRoot.setTop(top);
        learningRoot.setCenter(cardArea);
        scene.setRoot(learningRoot);
        if (initialDocument != null) {
            loadCard.run();
        }
    }

    private javafx.scene.control.ListCell<SQLiteStore.Document> documentCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SQLiteStore.Document item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        };
    }

    private void showLearningCard(List<SQLiteStore.Card> cards, int[] index, Label prompt,
                                  Label answer, Button reveal, HBox grading, Label progressLabel) {
        boolean available = index[0] < cards.size();
        prompt.setText(available ? cards.get(index[0]).front() : "No cards need review.");
        answer.setText(available ? cards.get(index[0]).translation() : "");
        answer.setVisible(false);
        reveal.setDisable(!available);
        grading.setVisible(false);
        progressLabel.setText(available ? "Card " + (index[0] + 1) + " of " + cards.size() : "");
    }

    private void gradeCard(boolean correct, List<SQLiteStore.Card> cards, int[] index,
                           SQLiteStore.Card[] lastCard, Button undoButton, Label prompt,
                           Label answer, Button reveal, HBox grading, Label progressLabel) {
        if (index[0] >= cards.size()) {
            return;
        }
        try {
            lastCard[0] = cards.get(index[0]);
            store.recordAnswer(lastCard[0].id(), correct);
            index[0]++;
            undoButton.setDisable(false);
            showLearningCard(cards, index, prompt, answer, reveal, grading, progressLabel);
        } catch (Exception exception) {
            error("Could not save answer", exception);
        }
    }

    private void undoCard(List<SQLiteStore.Card> cards, int[] index, SQLiteStore.Card[] lastCard,
                          Button undoButton, Label prompt, Label answer, Button reveal,
                          HBox grading, Label progressLabel) {
        if (lastCard[0] == null || index[0] == 0) {
            return;
        }
        try {
            store.restoreProgress(lastCard[0]);
            index[0]--;
            lastCard[0] = null;
            undoButton.setDisable(true);
            showLearningCard(cards, index, prompt, answer, reveal, grading, progressLabel);
        } catch (Exception exception) {
            error("Could not undo answer", exception);
        }
    }

    private VBox readerPanel() {
        reader.setEditable(false);
        reader.setWrapText(true);
        reader.setPromptText("The selected document page will appear here.");
        reader.setStyle("-fx-font-size: 16px;");
        pageImage.setPreserveRatio(true);
        pageImage.setSmooth(true);
        pageImage.setFitWidth(740);
        pageImage.setFitHeight(560);
        documentScroll.setFitToWidth(true);
        documentScroll.setFitToHeight(true);
        documentScroll.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        documentScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        documentScroll.setStyle("-fx-background: #eef0f4; -fx-background-color: #eef0f4;");
        documentScroll.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) ->
                updateImageSize());
        pageView.setStyle("-fx-background-color: #eeeeee;");
        pageView.getChildren().add(reader);
        VBox.setVgrow(pageView, Priority.ALWAYS);
        VBox panel = new VBox(10, new Label("Reader"), pageView);
        panel.setPadding(new Insets(18, 18, 0, 0));
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private VBox sidebar() {
        Label title = new Label("Page vocabulary");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        wordsSidebar.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(wordsSidebar);
        scroll.setFitToWidth(true);
        scroll.setFocusTraversable(false);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox panel = new VBox(10, title, new Separator(), scroll);
        panel.setPrefWidth(340);
        panel.setPadding(new Insets(18, 0, 0, 0));
        return panel;
    }

    private void chooseDocument(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Documents and images", "*.pdf", "*.ppt", "*.pptx", "*.png", "*.jpg", "*.jpeg", "*.tif", "*.tiff"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            loadDocument(file.toPath());
        }
    }

    private void loadDocument(Path path) {
        status.setText("Reading " + path.getFileName() + "...");
        Task<List<DocumentReader.Page>> task = new Task<>() {
            @Override
            protected List<DocumentReader.Page> call() throws Exception {
                return DocumentReader.read(path);
            }
        };
        task.setOnSucceeded(event -> {
            try {
                documentId = store.addDocument(path.getFileName().toString(), path.toAbsolutePath().toString());
                pages = task.getValue();
                pageIndex = 0;
                scene.setRoot(readerRoot);
                showPage(0);
                status.setText("Opened " + path.getFileName() + " (" + pages.size() + " page(s)).");
            } catch (Exception exception) {
                error("Could not save document set", exception);
            }
        });
        task.setOnFailed(event -> error("Could not read document", task.getException()));
        Thread worker = new Thread(task, "document-reader");
        worker.setDaemon(true);
        worker.start();
    }

    private void showPage(int index) {
        if (pages.isEmpty() || index < 0 || index >= pages.size()) {
            return;
        }
        pageIndex = index;
        DocumentReader.Page page = pages.get(index);
        if (page.image() != null) {
            pageImage.setImage(SwingFXUtils.toFXImage(page.image(), null));
            documentScroll.setContent(pageImage);
            pageView.getChildren().setAll(documentScroll);
            updateImageSize();
        } else {
            reader.setText(page.text());
            pageView.getChildren().setAll(reader);
        }
        pageLabel.setText("Page " + page.number() + " / " + pages.size());
        pageTranslations = Map.of();
        vocabularyFocus = -1;
        showPendingWords(page.words());
        translatePage(page.words());
    }

    private void showPendingWords(List<String> words) {
        wordsSidebar.getChildren().clear();
        vocabularyButtons.clear();
        for (String word : words) {
            addTranslatedWord(word, null);
        }
    }

    private void translatePage(List<String> words) {
        status.setText("Translating " + words.size() + " word(s)...");
        Task<Map<String, String>> task = new Task<>() {
            @Override
            protected Map<String, String> call() throws Exception {
                return translator.translateAll(words);
            }
        };
        task.setOnSucceeded(event -> {
            pageTranslations = task.getValue();
            wordsSidebar.getChildren().clear();
            vocabularyButtons.clear();
            for (String word : words) {
                addTranslatedWord(word, pageTranslations.get(word));
            }
            status.setText("Translated " + pageTranslations.size() + " word(s). Click a word to reveal it.");
        });
        task.setOnFailed(event -> error("Could not translate page vocabulary", task.getException()));
        Thread worker = new Thread(task, "translate-page");
        worker.setDaemon(true);
        worker.start();
    }

    private void addTranslatedWord(String word, String translation) {
        Label wordLabel = new Label(word);
        Label definition = new Label("Click to reveal definition");
        definition.setStyle("-fx-text-fill: #777777;");
        VBox text = new VBox(3, wordLabel, definition);
        Button card = new Button();
        card.setGraphic(text);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-border-color: #dddddd;");
        card.setOnAction(event -> revealDefinition(word, translation, definition, card));
        card.setFocusTraversable(true);
        card.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (isFocused) {
                vocabularyFocus = vocabularyButtons.indexOf(card);
                card.setStyle("-fx-background-color: #ede9fe; -fx-border-color: #7c3aed; "
                        + "-fx-border-width: 2;");
            } else {
                card.setStyle("-fx-background-color: white; -fx-border-color: #dddddd;");
            }
        });
        vocabularyButtons.add(card);
        wordsSidebar.getChildren().add(card);
    }

    private void handleReaderKey(KeyEvent event) {
        if (scene.getRoot() != readerRoot || pages.isEmpty()) {
            return;
        }
        if (event.getCode() == KeyCode.LEFT) {
            showPage(pageIndex - 1);
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT) {
            showPage(pageIndex + 1);
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            focusVocabulary(vocabularyFocus < 0 ? vocabularyButtons.size() - 1 : vocabularyFocus - 1);
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            focusVocabulary(vocabularyFocus < 0 ? 0 : vocabularyFocus + 1);
            event.consume();
        } else if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)
                && vocabularyFocus >= 0 && vocabularyFocus < vocabularyButtons.size()) {
            vocabularyButtons.get(vocabularyFocus).fire();
            event.consume();
        }
    }

    private void focusVocabulary(int index) {
        if (vocabularyButtons.isEmpty()) {
            return;
        }
        vocabularyFocus = Math.max(0, Math.min(index, vocabularyButtons.size() - 1));
        vocabularyButtons.get(vocabularyFocus).requestFocus();
    }

    private void changeZoom(double amount) {
        zoom = Math.max(0.5, Math.min(2.5, zoom + amount));
        updateImageSize();
    }

    private void resetZoom() {
        zoom = 1.0;
        updateImageSize();
    }

    private void updateImageSize() {
        if (pageImage.getImage() == null) {
            return;
        }
        double availableWidth = Math.max(300, documentScroll.getViewportBounds().getWidth() - 24);
        double availableHeight = Math.max(250, documentScroll.getViewportBounds().getHeight() - 24);
        double imageWidth = pageImage.getImage().getWidth();
        double imageHeight = pageImage.getImage().getHeight();
        double fit = Math.min(availableWidth / imageWidth, availableHeight / imageHeight);
        fit = Math.max(fit, 0.1) * zoom;
        pageImage.setFitWidth(imageWidth * fit);
        pageImage.setFitHeight(imageHeight * fit);
    }

    private void revealDefinition(String word, String translation, Label definition, Button card) {
        if (!"Click to reveal definition".equals(definition.getText())) {
            return;
        }
        if (translation == null || translation.isBlank()) {
            error("Translation unavailable", new IllegalStateException("This word was not translated."));
            return;
        }
        card.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                store.addCard(documentId, word, translation);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            definition.setText(translation);
            definition.setStyle("-fx-text-fill: #176b3a; -fx-font-weight: bold;");
            card.setDisable(false);
            status.setText("Added \"" + word + "\" to this document's flashcard set.");
        });
        task.setOnFailed(event -> {
            card.setDisable(false);
            error("Could not translate word", task.getException());
        });
        Thread worker = new Thread(task, "translate-word");
        worker.setDaemon(true);
        worker.start();
    }

    private void error(String title, Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }
}
