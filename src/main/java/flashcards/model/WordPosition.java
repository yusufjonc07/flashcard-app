package flashcards.model;

/**
 * A piece of text on a PDF page (usually everything between two spaces) and where it is.
 * Coordinates are in PDF points from the top-left corner of the page.
 *
 * <p>{@code charLeft[i]} and {@code charRight[i]} are the left and right edges of character i
 * of {@link #getWord()}, so a box can be drawn around just part of the text, for example
 * around 응답 inside 질의응답및발표자전환.
 */
public class WordPosition {

    private final String word;
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final int pageNumber;
    private final float[] charLeft;
    private final float[] charRight;

    public WordPosition(String word, float x, float y, float width, float height, int pageNumber) {
        this(word, x, y, width, height, pageNumber, null, null);
    }

    public WordPosition(String word, float x, float y, float width, float height, int pageNumber,
                        float[] charLeft, float[] charRight) {
        this.word = word;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.pageNumber = pageNumber;
        this.charLeft = charLeft;
        this.charRight = charRight;
    }

    public String getWord() { return word; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public int getPageNumber() { return pageNumber; }

    /** Left edge of the characters [start, start + length), or the word's left edge if unknown. */
    public float leftOf(int start, int length) {
        return hasCharPositions(start, length) ? charLeft[start] : x;
    }

    /** Width of the characters [start, start + length), or the whole word's width if unknown. */
    public float widthOf(int start, int length) {
        return hasCharPositions(start, length)
                ? charRight[start + length - 1] - charLeft[start]
                : width;
    }

    private boolean hasCharPositions(int start, int length) {
        return charLeft != null && charRight != null && length > 0
                && start >= 0 && start + length <= charLeft.length
                && charLeft.length == word.length();
    }
}
