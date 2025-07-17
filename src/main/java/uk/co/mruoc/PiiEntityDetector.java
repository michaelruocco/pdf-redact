package uk.co.mruoc;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;

@RequiredArgsConstructor
@Slf4j
public class PiiEntityDetector {

    private final ComprehendClient comprehendClient;
    private final String languageCode;

    public PiiEntityDetector(Region region) {
        this(ComprehendClient.builder().region(region).build(), "en");
    }

    public Collection<Page> detectEntities(Collection<Page> pages) {
        return pages.stream().map(this::detectEntities).toList();
    }

    private Page detectEntities(Page page) {
        var start = Instant.now();
        try {
            var request = DetectPiiEntitiesRequest.builder()
                    .text(page.asText())
                    .languageCode(languageCode)
                    .build();
            var result = comprehendClient.detectPiiEntities(request);
            var entities = result.entities().stream()
                    .map(entity -> new PiiEntityText(entity, toText(entity, page.asText())))
                    .toList();
            return page.withEntities(entities);
        } finally {
            var duration = Duration.between(start, Instant.now());
            log.info("detect entities took {}", duration);
        }
    }

    private static String toText(PiiEntity entity, String text) {
        var entityText = text.substring(entity.beginOffset(), entity.endOffset());
        log.info("found entity {} {} {}", entityText, entity.typeAsString(), entity.score());
        return entityText;
    }
}
