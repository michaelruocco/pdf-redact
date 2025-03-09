package uk.co.mruoc;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.pdfcleanup.CleanUpProperties;
import com.itextpdf.pdfcleanup.PdfCleanUpLocation;
import com.itextpdf.pdfcleanup.PdfCleanUpTool;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;
import software.amazon.awssdk.services.textract.TextractClient;
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
            var pages = splitPagesToDocuments(input);
            var pagesWithText = detectText(pages);
            var pagesWithEntities = detectEntities(pagesWithText);
            pagesWithEntities.forEach(Page::debug);
            redact(input, redacted, pagesWithEntities);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("entire redaction took {}", duration);
        }
    }

    private Collection<Page> detectText(Collection<Page> pages) {
        var start = Instant.now();
        try {
            return pages.stream().map(this::addBlocks).toList();
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("detect text took {}", duration);
        }
    }

    private Page addBlocks(Page page) {
        var path = page.getPath();
        try (var sourceStream = new FileInputStream(path)) {
            var document = Document.builder()
                    .bytes(SdkBytes.fromInputStream(sourceStream))
                    .build();
            var request = DetectDocumentTextRequest.builder().document(document).build();
            var response = textractClient.detectDocumentText(request);
            var blocks = response.blocks().stream().toList();
            return page.withBlocks(blocks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            FileUtils.deleteQuietly(new File(path));
        }
    }

    private Collection<Page> detectEntities(Collection<Page> pages) {
        return pages.stream().map(this::detectEntities).toList();
    }

    private Page detectEntities(Page page) {
        var start = Instant.now();
        try {
            var request = DetectPiiEntitiesRequest.builder()
                    .text(page.asText())
                    .languageCode(piiEntitiesLanguageCode)
                    .build();
            var result = comprehendClient.detectPiiEntities(request);
            var entities = result.entities().stream()
                    .map(entity -> new PiiEntityText(entity, toText(entity, page.asText())))
                    .filter(shouldRedact)
                    .toList();
            return page.withEntities(entities);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("detect entities took {}", duration);
        }
    }

    private static List<PdfCleanUpLocation> toLocations(PdfDocument pdf, Collection<Page> pages) {
        return new ArrayList<>(pages.stream()
                .map(page -> toLocations(pdf, page))
                .flatMap(Collection::stream)
                .toList());
    }

    private static Collection<PdfCleanUpLocation> toLocations(PdfDocument pdf, Page page) {
        var entities = page.getEntities();
        var size = pdf.getPage(page.getNumber()).getPageSize();
        var locations = new ArrayList<PdfCleanUpLocation>();
        for (var entity : entities) {
            var blocks = page.findWordBlocksByText(entity.getText());
            for (var block : blocks) {
                var box = block.geometry().boundingBox();
                var height = box.height() * size.getHeight();
                var rectangle = new Rectangle(
                        box.left() * size.getWidth(),
                        (size.getHeight() - height) - (box.top() * size.getHeight()),
                        box.width() * size.getWidth(),
                        height);
                var location = new PdfCleanUpLocation(page.getNumber(), rectangle, ColorConstants.BLACK);
                locations.add(location);
            }
        }
        return locations;
    }

    private void redact(File inputFile, File redactedFile, Collection<Page> pages) {
        var start = Instant.now();
        try (var pdf = new PdfDocument(new PdfReader(inputFile.getAbsolutePath()), new PdfWriter(redactedFile))) {
            var locations = toLocations(pdf, pages);
            PdfCleanUpTool tool = new PdfCleanUpTool(pdf, locations, new CleanUpProperties());
            tool.cleanUp();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("redaction took {} redacted file at {}", duration, redactedFile.getAbsolutePath());
        }
    }

    private static Collection<Page> splitPagesToDocuments(File file) {
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
                Collection<Page> pages = new ArrayList<>();
                for (String path : splitter.getPaths()) {
                    pages.add(new Page(pages.size() + 1, path));
                }
                return pages;
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
}
