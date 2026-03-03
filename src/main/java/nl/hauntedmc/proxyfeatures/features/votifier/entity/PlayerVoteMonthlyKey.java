package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PlayerVoteMonthlyKey implements Serializable {

    @Column(name = "player_id", nullable = false)
    private long playerId;

    @Column(name = "month_year_month", nullable = false)
    private int monthYearMonth;

    public PlayerVoteMonthlyKey() {
    }

    public PlayerVoteMonthlyKey(long playerId, int monthYearMonth) {
        this.playerId = playerId;
        this.monthYearMonth = monthYearMonth;
    }

    public long getPlayerId() {
        return playerId;
    }

    public int getMonthYearMonth() {
        return monthYearMonth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerVoteMonthlyKey that)) return false;
        return playerId == that.playerId && monthYearMonth == that.monthYearMonth;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, monthYearMonth);
    }
}