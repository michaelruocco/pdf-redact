package uk.co.mruoc;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

@RequiredArgsConstructor
@Slf4j
public class TextExtractor {

    private final TextractClient client;

    public TextExtractor(Region region) {
        this(TextractClient.builder().region(region).build());
    }

    public Collection<Page> extract(File input) {
        log.info("starting detect text");
        var start = Instant.now();
        try {
            var pages = toDocumentPerPage(input);
            return detectText(pages);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("detect text took {}", duration);
        }
    }

    private Collection<Page> toDocumentPerPage(File file) {
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

    private Collection<Page> detectText(Collection<Page> pages) {
        return pages.stream().map(this::addBlocks).toList();
    }

    private Page addBlocks(Page page) {
        var start = Instant.now();
        var path = page.getPath();
        try (var sourceStream = new FileInputStream(path)) {
            var document = Document.builder()
                    .bytes(SdkBytes.fromInputStream(sourceStream))
                    .build();
            var request = DetectDocumentTextRequest.builder().document(document).build();
            var response = client.detectDocumentText(request);
            var blocks = response.blocks().stream().toList();
            blocks.forEach(block -> log.debug("extracted block {}", block));
            return page.withBlocks(blocks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            FileUtils.deleteQuietly(new File(path));
            var duration = Duration.between(start, Instant.now());
            log.info("detect page text for {} took {}", path, duration);
        }
    }
}
