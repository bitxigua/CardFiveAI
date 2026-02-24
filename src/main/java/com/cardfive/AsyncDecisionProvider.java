package com.cardfive;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 通用异步决策提供者，通过交互网关进行玩家输入。
 */
public class AsyncDecisionProvider implements HumanDecisionProvider {
    private final PlayerInteractionHandler handler;

    public AsyncDecisionProvider(PlayerInteractionHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean shouldHu(List<Card> hand, int fan) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        handler.requestSelfHu(hand, fan, future::complete);
        return waitFor(future);
    }

    @Override
    public int chooseDiscardIndex(List<Card> hand) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        handler.requestDiscardSelection(hand, future::complete);
        return waitFor(future);
    }

    @Override
    public ReactionType chooseReaction(Card tile, List<Card> hand, boolean canPeng, boolean canGang) {
        CompletableFuture<ReactionType> future = new CompletableFuture<>();
        handler.requestReaction(tile, canPeng, canGang, future::complete);
        return waitFor(future);
    }

    @Override
    public boolean shouldHuOnDiscard(Card tile, List<Card> hand, List<Meld> melds,
                                     List<WinCategory> categories, int fan) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        handler.requestDiscardHu(tile, hand, melds, new ArrayList<>(categories), fan, future::complete);
        return waitFor(future);
    }

    private <T> T waitFor(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待用户输入时被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("获取用户输入失败", e);
        }
    }
}
