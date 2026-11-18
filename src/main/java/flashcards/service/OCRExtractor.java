package flashcards.service;

import flashcards.AppPaths;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import java.io.File;

public class OCRExtractor implements TextExtractor {

    File filePath;

    public OCRExtractor(String pathname) {
        filePath = new File(pathname);
    }

    public String extract() throws Exception {
        Tesseract tesseract = new Tesseract();

        tesseract.setDatapath(AppPaths.tessdata().toString());
        tesseract.setLanguage("kor+eng");

        try {
            return tesseract.doOCR(filePath);
        } catch (TesseractException e) {
            throw e;
        }
    }


}
