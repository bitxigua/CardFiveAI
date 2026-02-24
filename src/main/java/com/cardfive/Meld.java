package com.cardfive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 玩家已落地的牌组。
 */
public final class Meld {
    private final List<Card> cards;
    private final MeldType type;
    private final boolean concealed;

    public Meld(List<Card> cards, MeldType type, boolean concealed) {
        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("牌组不能为空");
        }
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        this.type = Objects.requireNonNull(type, "牌组类型不能为空");
        this.concealed = concealed;
    }

    public List<Card> getCards() {
        return cards;
    }

    public MeldType getType() {
        return type;
    }

    public boolean isConcealed() {
        return concealed;
    }
}
