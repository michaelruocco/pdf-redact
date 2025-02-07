package uk.co.mruoc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;
import software.amazon.awssdk.services.comprehend.model.PiiEntityType;

@RequiredArgsConstructor
@Getter
public class PiiEntityText {

    private final PiiEntity entity;
    private final String text;

    public PiiEntityType getType() {
        return entity.type();
    }

    public float getScore() {
        return entity.score();
    }
}
