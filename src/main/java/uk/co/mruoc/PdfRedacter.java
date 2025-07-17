package uk.co.mruoc;

import static com.itextpdf.kernel.colors.ColorConstants.BLACK;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.pdfcleanup.CleanUpProperties;
import com.itextpdf.pdfcleanup.PdfCleanUpLocation;
import com.itextpdf.pdfcleanup.PdfCleanUpTool;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.textract.model.Block;

@RequiredArgsConstructor
@Slf4j
public class PdfRedacter {

    private final Predicate<PiiEntityText> shouldRedact;

    public PdfRedacter() {
        this(new DefaultRedactPiiEntityTextPredicate());
    }

    public void redact(File inputFile, File redactedFile, Collection<Page> pages) {
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

    private List<PdfCleanUpLocation> toLocations(PdfDocument pdf, Collection<Page> pages) {
        return new ArrayList<>(pages.stream()
                .map(page -> toLocations(pdf, page))
                .flatMap(Collection::stream)
                .toList());
    }

    private Collection<PdfCleanUpLocation> toLocations(PdfDocument pdf, Page page) {
        // var entityLocations = toEntityLocations(pdf, page, page.getEntities());
        Collection<PdfCleanUpLocation> entityLocations = Collections.emptyList();
        var handWrittenLocations = toHandWrittenLocations(pdf, page);
        return Stream.concat(entityLocations.stream(), handWrittenLocations.stream())
                .toList();
    }

    private Collection<PdfCleanUpLocation> toEntityLocations(
            PdfDocument pdf, Page page, Collection<PiiEntityText> entities) {
        var entitiesToRedact = entities.stream().filter(shouldRedact).toList();
        entitiesToRedact.forEach(entity -> log.info("redacting {} {}", entity.getType(), entity.getText()));
        var pageSize = pdf.getPage(page.getNumber()).getPageSize();
        return entitiesToRedact.stream()
                .map(entity -> page.findBlocksByText(entity.getText()))
                .flatMap(Collection::stream)
                .map(block -> toRectangle(pageSize, block))
                .map(rectangle -> new PdfCleanUpLocation(page.getNumber(), rectangle, BLACK))
                .toList();
    }

    private Collection<PdfCleanUpLocation> toHandWrittenLocations(PdfDocument pdf, Page page) {
        var pageSize = pdf.getPage(page.getNumber()).getPageSize();
        return page.getAllHandWrittenBlocks().stream()
                .map(block -> toRectangle(pageSize, block))
                .map(rectangle -> new PdfCleanUpLocation(page.getNumber(), rectangle, BLACK))
                .toList();
    }

    private Rectangle toRectangle(Rectangle pageSize, Block block) {
        var box = block.geometry().boundingBox();
        var height = box.height() * pageSize.getHeight();
        var x = box.left() * pageSize.getWidth();
        var y = (pageSize.getHeight() - height) - (box.top() * pageSize.getHeight());
        var width = box.width() * pageSize.getWidth();
        if (block.text().equals("Xiomara")) {
            System.out.println(block + " " + new Rectangle(x, y, width, height));
        }
        return new Rectangle(x, y, width, height);
    }
}
