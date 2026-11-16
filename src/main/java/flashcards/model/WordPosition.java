package flashcards.model;

public class WordPosition {

    private final String word;
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final int pageNumber;

    public WordPosition(
            String word,
            float x,
            float y,
            float width,
            float height,
            int pageNumber) {

        this.word = word;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.pageNumber = pageNumber;
    }

    public String getWord() { return word; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public int getPageNumber() { return pageNumber; }
}