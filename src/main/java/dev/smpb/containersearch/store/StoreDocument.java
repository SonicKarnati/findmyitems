package dev.smpb.containersearch.store;

import dev.smpb.containersearch.index.IndexSnapshot;
import dev.smpb.containersearch.index.IndexedContainer;
import dev.smpb.containersearch.model.BlockPosition;
import dev.smpb.containersearch.model.ContainerKind;
import dev.smpb.containersearch.model.SlotSnapshot;
import dev.smpb.containersearch.model.SourceKey;
import dev.smpb.containersearch.model.StackKey;
import dev.smpb.containersearch.model.StackSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record StoreDocument(
        int schemaVersion,
        String worldLabel,
        String playerUuid,
        List<ContainerDto> containers,
        List<RequestDto> requests) {
    static final int CURRENT_SCHEMA = 1;

    static StoreDocument from(
            WorldKey key,
            IndexSnapshot snapshot,
            List<SavedCraftRequest> requests) {
        return new StoreDocument(
                CURRENT_SCHEMA,
                key.label(),
                key.playerId().toString(),
                snapshot.containers().stream().map(ContainerDto::from).toList(),
                requests.stream().map(RequestDto::from).toList());
    }

    StoreResult toResult(LoadStatus status) {
        UUID.fromString(playerUuid);
        return new StoreResult(
                new IndexSnapshot(containers.stream().map(ContainerDto::toDomain).toList()),
                requests.stream().map(RequestDto::toDomain).toList(),
                status,
                "");
    }

    record PositionDto(int x, int y, int z) {
        static PositionDto from(BlockPosition position) {
            return new PositionDto(position.x(), position.y(), position.z());
        }

        BlockPosition toDomain() {
            return new BlockPosition(x, y, z);
        }
    }

    record SourceDto(String dimension, String kind, List<PositionDto> positions) {
        static SourceDto from(SourceKey source) {
            return new SourceDto(
                    source.dimension(),
                    source.kind().name(),
                    source.positions().stream().map(PositionDto::from).toList());
        }

        SourceKey toDomain() {
            return new SourceKey(
                    dimension,
                    ContainerKind.valueOf(kind),
                    positions.stream().map(PositionDto::toDomain).toList());
        }
    }

    record StackDto(
            String itemId,
            String componentsJson,
            int count,
            String displayName,
            List<String> tooltip) {
        static StackDto from(StackSnapshot stack) {
            return new StackDto(
                    stack.key().itemId(),
                    stack.key().componentsJson(),
                    stack.count(),
                    stack.displayName(),
                    stack.tooltip());
        }

        StackSnapshot toDomain() {
            return new StackSnapshot(
                    new StackKey(itemId, componentsJson), count, displayName, tooltip);
        }
    }

    record SlotDto(int slotIndex, StackDto stack) {
        static SlotDto from(SlotSnapshot slot) {
            return new SlotDto(slot.slotIndex(), StackDto.from(slot.stack()));
        }

        SlotSnapshot toDomain() {
            return new SlotSnapshot(slotIndex, stack.toDomain());
        }
    }

    record ContainerDto(
            SourceDto contentsKey,
            List<SourceDto> accessSources,
            List<SlotDto> slots,
            String observedAt) {
        static ContainerDto from(IndexedContainer container) {
            return new ContainerDto(
                    SourceDto.from(container.contentsKey()),
                    container.accessSources().stream().map(SourceDto::from).toList(),
                    container.slots().stream().map(SlotDto::from).toList(),
                    container.observedAt().toString());
        }

        IndexedContainer toDomain() {
            return new IndexedContainer(
                    contentsKey.toDomain(),
                    accessSources.stream().map(SourceDto::toDomain).toList(),
                    slots.stream().map(SlotDto::toDomain).toList(),
                    Instant.parse(observedAt));
        }
    }

    record RequestDto(String itemId, String componentsJson, int count) {
        static RequestDto from(SavedCraftRequest request) {
            return new RequestDto(
                    request.output().itemId(), request.output().componentsJson(), request.count());
        }

        SavedCraftRequest toDomain() {
            return new SavedCraftRequest(new StackKey(itemId, componentsJson), count);
        }
    }
}

