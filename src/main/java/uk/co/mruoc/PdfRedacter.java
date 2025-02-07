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
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

@Builder
@Slf4j
public class PdfRedacter {

    private final TextractClient textractClient;
    private final ComprehendClient comprehendClient;
    private final Predicate<PiiEntityText> shouldRedact;
    private final String piiEntitiesLanguageCode;

    public void redact(File input, File redacted) {
        var start = Instant.now();
        try {
            var text = detectText(input);
            var entities = detectEntities(text);
            redact(input, redacted, entities);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("entire redaction took {}", duration);
        }
    }

    private String detectText(File file) {
        var start = Instant.now();
        try {
            return splitPagesToDocuments(file).stream()
                    .map(this::toLines)
                    .flatMap(Collection::stream)
                    .collect(Collectors.joining(System.lineSeparator()));
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("detect text took {}", duration);
        }
    }

    private Collection<String> toLines(String pagePath) {
        try (var sourceStream = new FileInputStream(pagePath)) {
            var document = Document.builder()
                    .bytes(SdkBytes.fromInputStream(sourceStream))
                    .build();
            var request = DetectDocumentTextRequest.builder().document(document).build();
            var response = textractClient.detectDocumentText(request);
            return response.blocks().stream()
                    .filter(block -> block.blockType() == BlockType.LINE)
                    .map(Block::text)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            FileUtils.deleteQuietly(new File(pagePath));
        }
    }

    private Collection<PiiEntityText> detectEntities(String text) {
        var start = Instant.now();
        try {
            var request = DetectPiiEntitiesRequest.builder()
                    .text(text)
                    .languageCode(piiEntitiesLanguageCode)
                    .build();
            var result = comprehendClient.detectPiiEntities(request);
            return result.entities().stream()
                    .map(entity -> new PiiEntityText(entity, toText(entity, text)))
                    .filter(shouldRedact)
                    .toList();
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("detect entities took {}", duration);
        }
    }

    private void redact(File inputFile, File redactedFile, Collection<PiiEntityText> entities) {
        var strategy = buildStrategy(entities);
        var start = Instant.now();
        try (var pdf = new PdfDocument(new PdfReader(inputFile.getAbsolutePath()), new PdfWriter(redactedFile))) {
            PdfCleaner.autoSweepCleanUp(pdf, strategy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("redaction took {} redacted file at {}", duration, redactedFile.getAbsolutePath());
        }
    }

    private static Collection<String> splitPagesToDocuments(File file) {
        var start = Instant.now();
        try {
            try (PdfDocument original = new PdfDocument(new PdfReader(file.getAbsolutePath()))) {
                var splitter = PageIncrementingPdfSplitter.builder()
                        .document(original)
                        .folderPath(file.getParent())
                        .filename(file.getName())
                        .build();
                var pageDocuments = splitter.splitBySize(200000);
                for (var pageDocument : pageDocuments) {
                    pageDocument.close();
                }
                return splitter.getPaths();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("split pages took {}", duration);
        }
    }

    private static String toText(PiiEntity entity, String text) {
        var entityText = text.substring(entity.beginOffset(), entity.endOffset());
        log.debug("{} {} {}", entityText, entity.typeAsString(), entity.score());
        return entityText;
    }

    private static ICleanupStrategy buildStrategy(Collection<PiiEntityText> values) {
        var composite = new CompositeCleanupStrategy();
        values.stream()
                .map(value -> new RegexBasedCleanupStrategy(Pattern.compile(escapeSpecialCharacters(value.getText())))
                        .setRedactionColor(ColorConstants.BLACK))
                .forEach(composite::add);
        return composite;
    }

    private static String escapeSpecialCharacters(String input) {
        var escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                escaped.append("\\");
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
