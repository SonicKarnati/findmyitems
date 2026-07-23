package dev.smpb.containersearch;

import dev.smpb.containersearch.index.ContainerIndex;
import dev.smpb.containersearch.index.InMemoryContainerIndex;
import dev.smpb.containersearch.observation.ObservationCollector;
import net.fabricmc.api.ClientModInitializer;

public final class ContainerSearchClient implements ClientModInitializer {
    private ContainerIndex index;
    private ObservationCollector collector;

    @Override
    public void onInitializeClient() {
        index = new InMemoryContainerIndex();
        collector = new ObservationCollector(index);
    }
}
