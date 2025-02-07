package uk.co.mruoc;

import java.io.File;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.textract.TextractClient;

public class Main {

    public static void main(String[] args) {
        var region = Region.EU_WEST_1;
        var redacter = PdfRedacter.builder()
                .textractClient(TextractClient.builder().region(region).build())
                .comprehendClient(ComprehendClient.builder().region(region).build())
                .shouldRedact(new AllRedactPiiEntityTextPredicate())
                .piiEntitiesLanguageCode("en")
                .build();

        var inputFile = new File("input/example.pdf");
        var redactedFile = new File("redacted", inputFile.getName());
        redacter.redact(inputFile, redactedFile);
    }
}
