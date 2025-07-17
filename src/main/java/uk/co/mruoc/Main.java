package uk.co.mruoc;

import java.io.File;
import software.amazon.awssdk.regions.Region;

public class Main {

    public static void main(String[] args) {
        var region = Region.EU_WEST_1;
        var redacter = PiiPdfRedacter.builder()
                .extractor(new TextExtractor(region))
                .detector(new PiiEntityDetector(region))
                .redacter(new PdfRedacter())
                .build();

        var inputFile = new File("input/example.pdf");
        var redactedFile = new File("redacted", inputFile.getName());
        redacter.redact(inputFile, redactedFile);
    }
}
