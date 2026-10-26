package com.smxplore.recommendations.domain.exception;

import com.smxplore.recommendations.domain.model.ItemType;

import java.util.UUID;

public class ItemNotFoundException extends RuntimeException {

    private final ItemType itemType;
    private final UUID itemId;

    public ItemNotFoundException(ItemType itemType, UUID itemId) {
        super("Catalog item not found: " + itemType + "/" + itemId);
        this.itemType = itemType;
        this.itemId = itemId;
    }

    public ItemType itemType() {
        return itemType;
    }

    public UUID itemId() {
        return itemId;
    }
}
