package flashcards;


public class Main {

    public static void main(String[] args) throws Exception {

        String textContent;

        OCRExtractor txtImgExt = new OCRExtractor("/Users/yusufaxmad/Downloads/kowordcloud.jpeg");
        
        try{
            textContent = txtImgExt.extract();
        }catch(Exception e){
            throw e;
        }

        KoreanWordExtractor extractor = new KoreanWordExtractor(textContent);
        

        System.out.println(
            extractor
                .extract()
                .clean()
                .words
        );
    }
}