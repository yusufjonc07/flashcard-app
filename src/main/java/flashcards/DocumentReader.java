package flashcards;

import java.io.File;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;

public final class DocumentReader {

    public record Page(int number, String text, List<String> words, BufferedImage image) {
    }

    private DocumentReader() {
    }

    public static List<Page> read(Path path) throws Exception {
        return switch (extension(path)) {
            case "pdf" -> readPdf(path.toFile());
            case "pptx" -> readPptx(path.toFile());
            case "ppt" -> readLegacyPpt(path);
            case "png", "jpg", "jpeg", "tif", "tiff" ->
                    page(new OCRExtractor(path.toString()).extract());
            default -> throw new IOException("Unsupported file type: ." + extension(path));
        };
    }

    private static List<Page> readPdf(File file) throws IOException {
        List<Page> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(page(i, stripper.getText(document), renderer.renderImageWithDPI(i - 1, 120)));
            }
        }
        return pages;
    }

    private static List<Page> readPptx(File file) throws IOException {
        List<Page> pages = new ArrayList<>();
        try (FileInputStream input = new FileInputStream(file);
             XMLSlideShow show = new XMLSlideShow(input)) {
            int page = 1;
            for (XSLFSlide slide : show.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
                        text.append(textShape.getText()).append('\n');
                    }
                }
                Dimension size = show.getPageSize();
                BufferedImage image = new BufferedImage(size.width * 2, size.height * 2,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.scale(2, 2);
                slide.draw(graphics);
                graphics.dispose();
                pages.add(page(page++, text.toString(), image));
            }
        }
        return pages;
    }

    private static List<Page> readLegacyPpt(Path path) throws Exception {
        return page(new PPTExtractor(path.toString()).extract());
    }

    private static List<Page> page(String text) {
        return List.of(page(1, text));
    }

    private static Page page(int number, String text) {
        return page(number, text, null);
    }

    private static Page page(int number, String text, BufferedImage image) {
        return new Page(number, text, new KoreanWordExtractor(text).extract().clean().getWords(), image);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}
