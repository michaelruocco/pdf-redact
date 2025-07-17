package uk.co.mruoc;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
public class PiiPdfRedacter {

    private final TextExtractor extractor;
    private final PiiEntityDetector detector;
    private final PdfRedacter redacter;

    public void redact(File input, File redacted) {
        var start = Instant.now();
        try {
            var pagesWithText = extractor.extract(input);
            var pagesWithEntities = detector.detectEntities(pagesWithText);
            pagesWithEntities.forEach(Page::debug);
            redacter.redact(input, redacted, pagesWithEntities);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("entire redaction took {}", duration);
        }
    }
}
