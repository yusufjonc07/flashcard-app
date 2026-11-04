package flashcards;

import net.sourceforge.tess4j.Tesseract;
import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {

        Tesseract tesseract = new Tesseract();

        tesseract.setDatapath("tessdata");
        tesseract.setLanguage("kor+eng");

        String result = tesseract.doOCR(
            new File("/Users/yusufaxmad/Documents/screenshots/lecture.png")
        );

        KoreanWordExtractor extractor = new KoreanWordExtractor(result);

        System.out.println(extractor.extract());
    }
}