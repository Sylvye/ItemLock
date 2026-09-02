package com.bountysmp.itemlock.model;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class LockDefinition {
    private String id;
    private boolean enabled = true;
    private ItemStack sample;
    private MatchType matchType = MatchType.MATERIAL;
    private boolean depositProtection = true;
    private boolean placeProtection = true;
    private boolean dropProtection;
    private boolean burnProtection;
    private boolean explosionProtection;
    private boolean destructionMessage;
    private DestructionAudience destructionAudience = DestructionAudience.OPERATOR;
    private boolean destructionSoundEnabled = true;
    private String destructionSoundKey = "minecraft:entity.ender_dragon.growl";
    private long destroyedCount;
    private int order;

    public LockDefinition(String id) {
        this.id = sanitizeId(id == null || id.isBlank() ? "lock_" + UUID.randomUUID() : id);
    }

    public static LockDefinition create(ItemStack sample, MatchType matchType) {
        LockDefinition definition = new LockDefinition("lock_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        definition.setSample(sample);
        definition.setMatchType(matchType);
        return definition;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = sanitizeId(id);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ItemStack sample() {
        return sample == null ? null : sample.clone();
    }

    public void setSample(ItemStack sample) {
        this.sample = sample == null ? null : sample.clone();
        if (this.sample != null) {
            this.sample.setAmount(1);
        }
    }

    public MatchType matchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType == null ? MatchType.MATERIAL : matchType;
    }

    public boolean depositProtection() {
        return depositProtection;
    }

    public void setDepositProtection(boolean depositProtection) {
        this.depositProtection = depositProtection;
    }

    public boolean dropProtection() {
        return dropProtection;
    }

    public boolean placeProtection() {
        return placeProtection;
    }

    public void setPlaceProtection(boolean placeProtection) {
        this.placeProtection = placeProtection;
    }

    public void setDropProtection(boolean dropProtection) {
        this.dropProtection = dropProtection;
    }

    public boolean burnProtection() {
        return burnProtection;
    }

    public void setBurnProtection(boolean burnProtection) {
        this.burnProtection = burnProtection;
    }

    public boolean explosionProtection() {
        return explosionProtection;
    }

    public void setExplosionProtection(boolean explosionProtection) {
        this.explosionProtection = explosionProtection;
    }

    public boolean destructionMessage() {
        return destructionMessage;
    }

    public void setDestructionMessage(boolean destructionMessage) {
        this.destructionMessage = destructionMessage;
    }

    public DestructionAudience destructionAudience() {
        return destructionAudience;
    }

    public void setDestructionAudience(DestructionAudience destructionAudience) {
        this.destructionAudience = destructionAudience == null ? DestructionAudience.OPERATOR : destructionAudience;
    }

    public boolean destructionSoundEnabled() {
        return destructionSoundEnabled;
    }

    public void setDestructionSoundEnabled(boolean destructionSoundEnabled) {
        this.destructionSoundEnabled = destructionSoundEnabled;
    }

    public String destructionSoundKey() {
        return destructionSoundKey;
    }

    public void setDestructionSoundKey(String destructionSoundKey) {
        if (destructionSoundKey == null || destructionSoundKey.isBlank()) {
            this.destructionSoundKey = "minecraft:entity.ender_dragon.growl";
        } else {
            this.destructionSoundKey = destructionSoundKey;
        }
    }

    public long destroyedCount() {
        return destroyedCount;
    }

    public void setDestroyedCount(long destroyedCount) {
        this.destroyedCount = Math.max(0L, destroyedCount);
    }

    public void addDestroyed(long amount) {
        this.destroyedCount += Math.max(0L, amount);
    }

    public int order() {
        return order;
    }

    public void setOrder(int order) {
        this.order = Math.max(0, order);
    }

    public boolean valid() {
        return sample != null && !sample.getType().isAir();
    }

    public LockDefinition copy() {
        LockDefinition copy = new LockDefinition(id);
        copy.enabled = enabled;
        copy.sample = sample();
        copy.matchType = matchType;
        copy.depositProtection = depositProtection;
        copy.placeProtection = placeProtection;
        copy.dropProtection = dropProtection;
        copy.burnProtection = burnProtection;
        copy.explosionProtection = explosionProtection;
        copy.destructionMessage = destructionMessage;
        copy.destructionAudience = destructionAudience;
        copy.destructionSoundEnabled = destructionSoundEnabled;
        copy.destructionSoundKey = destructionSoundKey;
        copy.destroyedCount = destroyedCount;
        copy.order = order;
        return copy;
    }

    public String displayName() {
        return sample == null ? id : sample.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String sanitizeId(String input) {
        String sanitized = (input == null ? "lock" : input.toLowerCase(Locale.ROOT))
            .replace(':', '_')
            .replace('/', '_')
            .replaceAll("[^a-z0-9._-]", "_")
            .replaceAll("_+", "_");
        return sanitized.isBlank() ? "lock" : sanitized;
    }
}
