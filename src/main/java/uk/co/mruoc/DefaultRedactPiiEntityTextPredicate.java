package uk.co.mruoc;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.comprehend.model.PiiEntityType;

@RequiredArgsConstructor
public class DefaultRedactPiiEntityTextPredicate implements Predicate<PiiEntityText> {

    private final Collection<PiiEntityType> typesToIgnore;
    private final int minimumAddressSize;

    public DefaultRedactPiiEntityTextPredicate() {
        this(List.of(PiiEntityType.DATE_TIME, PiiEntityType.URL), 3);
    }

    @Override
    public boolean test(PiiEntityText entityText) {
        if (typesToIgnore.contains(entityText.getType())) {
            return false;
        }
        if (Objects.equals(PiiEntityType.ADDRESS, entityText.getType())) {
            return new StringTokenizer(entityText.getText()).countTokens() >= minimumAddressSize;
        }
        return true;
    }
}
