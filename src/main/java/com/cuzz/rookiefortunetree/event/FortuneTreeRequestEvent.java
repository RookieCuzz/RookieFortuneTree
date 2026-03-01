package com.cuzz.rookiefortunetree.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class FortuneTreeRequestEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Action {
        OPEN_GUI,
        WATER,
        COLLECT_BUBBLE,
        COLLECT_ALL
    }

    private final Player player;
    private final Action action;
    private final int bubbleIndex;
    private final Integer levelOverride;
    private boolean cancelled;
    private String cancelReason;

    public FortuneTreeRequestEvent(Player player, Action action) {
        this(player, action, -1, null);
    }

    public FortuneTreeRequestEvent(Player player, Action action, int bubbleIndex, Integer levelOverride) {
        super(false);
        this.player = player;
        this.action = action;
        this.bubbleIndex = bubbleIndex;
        this.levelOverride = levelOverride;
    }

    public Player getPlayer() {
        return player;
    }

    public Action getAction() {
        return action;
    }

    public int getBubbleIndex() {
        return bubbleIndex;
    }

    public Integer getLevelOverride() {
        return levelOverride;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

