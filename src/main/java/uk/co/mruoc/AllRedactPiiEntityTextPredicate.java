package uk.co.mruoc;

import java.util.function.Predicate;

public class AllRedactPiiEntityTextPredicate implements Predicate<PiiEntityText> {

    @Override
    public boolean test(PiiEntityText entityText) {
        return true;
    }
}
