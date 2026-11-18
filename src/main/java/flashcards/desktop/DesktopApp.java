package flashcards.desktop;

import flashcards.FlashcardApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.Desktop;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Desktop version: starts the Spring Boot server on localhost in the background and shows the
 * pages in a native window (JavaFX WebView) instead of a browser. Closing the window stops the
 * server.
 *
 * <p>Files are kept in {@code ~/Library/Application Support/Flashcards} (macOS) or
 * {@code ~/.flashcards} (other systems), so the app works no matter where it is installed.
 */
public class DesktopApp extends Application {

    /** Fixed port so the page origin (and the background saved in localStorage) stays the same. */
    private static final int PREFERRED_PORT = 18080;

    private ConfigurableApplicationContext server;

    public static void main(String[] args) throws IOException {
        Path data = appHome().resolve("data");
        Files.createDirectories(data);   // SQLite creates the database file but not its folder
        System.setProperty("flashcards.data", data.toString());
        if (System.getProperty("flashcards.tessdata") == null) {
            System.setProperty("flashcards.tessdata", "tessdata");
        }
        // Homebrew's libtesseract, needed for OCR of images (apps started from Finder get no PATH).
        if (System.getProperty("jna.library.path") == null) {
            System.setProperty("jna.library.path", "/opt/homebrew/lib:/usr/local/lib");
        }
        System.setProperty("java.awt.headless", "false");
        launch(DesktopApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        ProgressIndicator spinner = new ProgressIndicator();
        Label status = new Label("Starting Flashcards…");
        VBox loading = new VBox(16, spinner, status);
        loading.setStyle("-fx-alignment: center;");

        WebView web = new WebView();
        WebEngine engine = web.getEngine();
        engine.setUserDataDirectory(appHome().resolve("webview").toFile());
        engine.setConfirmHandler(DesktopApp::confirm);
        engine.setOnAlert(event -> new Alert(Alert.AlertType.INFORMATION, event.getData()).showAndWait());
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                stage.setTitle(engine.getTitle() == null ? "Flashcards" : engine.getTitle());
            }
        });

        StackPane root = new StackPane(web, loading);
        stage.setTitle("Flashcards");
        stage.setScene(new Scene(root, 1280, 860));
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.show();

        Thread starter = new Thread(() -> {
            try {
                int port = freePort();
                server = SpringApplication.run(FlashcardApplication.class,
                        "--server.port=" + port, "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off");
                String home = "http://127.0.0.1:" + port + "/";
                Platform.runLater(() -> {
                    // Links to other sites open in the normal browser, not inside the app.
                    engine.locationProperty().addListener((obs, old, url) -> {
                        if (url != null && !url.isEmpty() && !url.startsWith(home) && !url.startsWith("about:")) {
                            Platform.runLater(() -> engine.load(old == null ? home : old));
                            openInBrowser(url);
                        }
                    });
                    engine.load(home);
                    loading.setVisible(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "The app could not start:\n" + e.getMessage());
                    alert.showAndWait();
                    Platform.exit();
                });
            }
        }, "server-starter");
        starter.setDaemon(true);
        starter.start();
    }

    @Override
    public void stop() {
        if (server != null) {
            server.close();
        }
        System.exit(0);
    }

    private static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static void openInBrowser(String url) {
        new Thread(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ignored) {
                // No browser available; the link simply does nothing.
            }
        }).start();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(PREFERRED_PORT)) {
            return PREFERRED_PORT;
        } catch (IOException busy) {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            }
        }
    }

    static Path appHome() {
        String userHome = System.getProperty("user.home");
        Path home = System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? Path.of(userHome, "Library", "Application Support", "Flashcards")
                : Path.of(userHome, ".flashcards");
        try {
            Files.createDirectories(home);
        } catch (IOException ignored) {
            // Reported later when the database cannot be created.
        }
        return home;
    }
}
