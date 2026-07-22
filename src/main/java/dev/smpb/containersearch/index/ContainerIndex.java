package dev.smpb.containersearch.index;

import dev.smpb.containersearch.model.ContainerObservation;
import dev.smpb.containersearch.model.SourceKey;
import java.util.List;

public interface ContainerIndex {
    long revision();

    void observe(ContainerObservation observation);

    void markMissing(SourceKey source);

    List<ItemResult> search(String query);

    IndexSnapshot snapshot();

    void replace(IndexSnapshot snapshot);
}

