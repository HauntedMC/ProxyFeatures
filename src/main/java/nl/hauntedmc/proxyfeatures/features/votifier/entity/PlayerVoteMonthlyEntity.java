package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(
        name = "player_vote_monthly",
        indexes = {
                @Index(name = "idx_vote_monthly_month", columnList = "month_year_month"),
                @Index(name = "idx_vote_monthly_month_votes", columnList = "month_year_month, month_votes"),
                @Index(name = "idx_vote_monthly_player", columnList = "player_id"),
                @Index(name = "idx_vote_monthly_winner", columnList = "month_year_month, winner_rank"),
                @Index(name = "idx_vote_monthly_winner_pending", columnList = "player_id, winner_rank, winner_processing, winner_congrats_sent, winner_reward_granted")
        }
)
public class PlayerVoteMonthlyEntity {

    @EmbeddedId
    private PlayerVoteMonthlyKey id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", insertable = false, updatable = false, nullable = false)
    private PlayerEntity player;

    @Column(name = "month_votes", nullable = false)
    private int monthVotes;

    // Winner state for this specific month
    // 0 means not a winner, 1..3 means position
    @Column(name = "winner_rank", nullable = false)
    private int winnerRank;

    // Ensures medals are applied exactly once for this month and player
    @Column(name = "winner_medal_applied", nullable = false)
    private boolean winnerMedalApplied;

    // One time congrats state
    @Column(name = "winner_congrats_sent", nullable = false)
    private boolean winnerCongratsSent;

    // Reward state, tied to the same processing flow as congrats
    @Column(name = "winner_reward_granted", nullable = false)
    private boolean winnerRewardGranted;

    // Lightweight lock to avoid multiple proxies processing the same row at the same time
    @Column(name = "winner_processing", nullable = false)
    private boolean winnerProcessing;

    public PlayerVoteMonthlyEntity() {
    }

    public PlayerVoteMonthlyEntity(long playerId, int monthYearMonth) {
        this.id = new PlayerVoteMonthlyKey(playerId, monthYearMonth);
        this.monthVotes = 0;

        this.winnerRank = 0;
        this.winnerMedalApplied = false;
        this.winnerCongratsSent = false;
        this.winnerRewardGranted = false;
        this.winnerProcessing = false;
    }

    public PlayerVoteMonthlyKey getId() {
        return id;
    }

    public long getPlayerId() {
        return id == null ? 0L : id.getPlayerId();
    }

    public int getMonthYearMonth() {
        return id == null ? 0 : id.getMonthYearMonth();
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public int getMonthVotes() {
        return monthVotes;
    }

    public void setMonthVotes(int monthVotes) {
        this.monthVotes = monthVotes;
    }

    public int getWinnerRank() {
        return winnerRank;
    }

    public void setWinnerRank(int winnerRank) {
        this.winnerRank = winnerRank;
    }

    public boolean isWinnerMedalApplied() {
        return winnerMedalApplied;
    }

    public void setWinnerMedalApplied(boolean winnerMedalApplied) {
        this.winnerMedalApplied = winnerMedalApplied;
    }

    public boolean isWinnerCongratsSent() {
        return winnerCongratsSent;
    }

    public void setWinnerCongratsSent(boolean winnerCongratsSent) {
        this.winnerCongratsSent = winnerCongratsSent;
    }

    public boolean isWinnerRewardGranted() {
        return winnerRewardGranted;
    }

    public void setWinnerRewardGranted(boolean winnerRewardGranted) {
        this.winnerRewardGranted = winnerRewardGranted;
    }

    public boolean isWinnerProcessing() {
        return winnerProcessing;
    }

    public void setWinnerProcessing(boolean winnerProcessing) {
        this.winnerProcessing = winnerProcessing;
    }
}