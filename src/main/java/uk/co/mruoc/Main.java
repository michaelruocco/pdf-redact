package uk.co.mruoc;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.pdfcleanup.PdfCleaner;
import com.itextpdf.pdfcleanup.autosweep.CompositeCleanupStrategy;
import com.itextpdf.pdfcleanup.autosweep.ICleanupStrategy;
import com.itextpdf.pdfcleanup.autosweep.RegexBasedCleanupStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.Entity;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

public class Main {

    public static void main(String[] args) throws IOException {
        String filename = "example.pdf";
        var region = Region.EU_WEST_1;

        var textractClient = TextractClient.builder().region(region).build();
        var text = detectText(textractClient, filename);

        var comprehendClient = ComprehendClient.builder().region(region).build();
        var entities = detectEntities(comprehendClient, text);

        var strategy = buildStrategy(entities);
        redact(filename, strategy);
    }

    public static String detectText(TextractClient client, String path) throws IOException {
        try (var sourceStream = new FileInputStream(path)) {
            var document = Document.builder()
                    .bytes(SdkBytes.fromInputStream(sourceStream))
                    .build();
            var request = DetectDocumentTextRequest.builder().document(document).build();
            var response = client.detectDocumentText(request);
            return response.blocks().stream()
                    .filter(block -> block.blockType() == BlockType.LINE)
                    .map(Block::text)
                    .collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private static Collection<String> detectEntities(ComprehendClient client, String text) {
        var request =
                DetectEntitiesRequest.builder().text(text).languageCode("en").build();
        var result = client.detectEntities(request);
        result.entities().stream().map(Entity::type).distinct().forEach(System.out::println);
        return result.entities().stream().map(Entity::text).toList();
    }

    private static ICleanupStrategy buildStrategy(Collection<String> values) {
        var composite = new CompositeCleanupStrategy();
        values.stream()
                .map(value ->
                        new RegexBasedCleanupStrategy(Pattern.compile(value)).setRedactionColor(ColorConstants.BLACK))
                .forEach(composite::add);
        return composite;
    }

    private static void redact(String filename, ICleanupStrategy strategy) throws IOException {
        try (var pdf = new PdfDocument(new PdfReader(filename), new PdfWriter(new File("redacted", filename)))) {
            PdfCleaner.autoSweepCleanUp(pdf, strategy);
        }
    }
}
