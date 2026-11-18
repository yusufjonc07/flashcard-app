package flashcards.desktop;

/**
 * Entry point of the desktop app. A separate class that does not extend
 * {@link javafx.application.Application}, so JavaFX can be loaded from the normal classpath.
 */
public class DesktopMain {

    public static void main(String[] args) throws Exception {
        DesktopApp.main(args);
    }
}
