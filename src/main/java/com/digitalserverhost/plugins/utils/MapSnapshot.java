package com.digitalserverhost.plugins.utils;

import java.io.Serializable;

/**
 * Data container representing a stashed or synchronized filled map item across servers.
 */
public class MapSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceServerId;
    private int originalMapId;
    private int slot;
    private String inventoryType; // MAIN, ARMOR, ENDERCHEST
    private String itemNBT;
    private byte[] canvasPixels; // 128x128 byte buffer for global mode

    public MapSnapshot() {}

    public MapSnapshot(String sourceServerId, int originalMapId, int slot, String inventoryType, String itemNBT) {
        this.sourceServerId = sourceServerId;
        this.originalMapId = originalMapId;
        this.slot = slot;
        this.inventoryType = inventoryType;
        this.itemNBT = itemNBT;
    }

    public String getSourceServerId() {
        return sourceServerId;
    }

    public void setSourceServerId(String sourceServerId) {
        this.sourceServerId = sourceServerId;
    }

    public int getOriginalMapId() {
        return originalMapId;
    }

    public void setOriginalMapId(int originalMapId) {
        this.originalMapId = originalMapId;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public String getInventoryType() {
        return inventoryType;
    }

    public void setInventoryType(String inventoryType) {
        this.inventoryType = inventoryType;
    }

    public String getItemNBT() {
        return itemNBT;
    }

    public void setItemNBT(String itemNBT) {
        this.itemNBT = itemNBT;
    }

    public byte[] getCanvasPixels() {
        return canvasPixels;
    }

    public void setCanvasPixels(byte[] canvasPixels) {
        this.canvasPixels = canvasPixels;
    }
}
