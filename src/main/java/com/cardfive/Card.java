package com.cardfive;

/**
 * 表示一张卡五星麻将牌。
 */
public final class Card {
    public enum Suit {
        TIAO("条"),
        TONG("筒");

        private final String displayName;

        Suit(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Honor {
        HONG_ZHONG("红中"),
        FA_CAI("发财"),
        BAI_BAN("白板");

        private final String displayName;

        Honor(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Suit suit;
    private final Integer rank;
    private final Honor honor;

    /**
     * 创建一张带花色的牌。
     */
    public Card(Suit suit, int rank) {
        if (suit == null) {
            throw new IllegalArgumentException("Suit cannot be null.");
        }
        if (rank < 1 || rank > 9) {
            throw new IllegalArgumentException("Rank must be between 1 and 9.");
        }
        this.suit = suit;
        this.rank = rank;
        this.honor = null;
    }

    /**
     * 创建一张字牌（无点数）。
     */
    public Card(Honor honor) {
        if (honor == null) {
            throw new IllegalArgumentException("Honor cannot be null.");
        }
        this.suit = null;
        this.rank = null;
        this.honor = honor;
    }

    public boolean isHonor() {
        return honor != null;
    }

    public Suit getSuit() {
        return suit;
    }

    public Integer getRank() {
        return rank;
    }

    public Honor getHonor() {
        return honor;
    }

    @Override
    public String toString() {
        return isHonor() ? honor.getDisplayName() : suit.getDisplayName() + rank;
    }
}
