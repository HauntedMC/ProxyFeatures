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
                @Index(name = "idx_vote_monthly_final_rank", columnList = "month_year_month, final_rank"),
                @Index(name = "idx_vote_monthly_pending", columnList = "player_id, processing, notify_sent, reward_granted, month_year_month"),
                @Index(name = "idx_vote_monthly_processing_stale", columnList = "processing, processing_at")
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

    // Final rank for this month
    // 0 means not computed yet (non winners can be computed lazily on first login)
    // 1..N means final place based on monthVotes desc then playerId asc
    @Column(name = "final_rank", nullable = false)
    private int finalRank;

    // Lifetime medals increment guard (applies to top 3 only)
    @Column(name = "medal_applied", nullable = false)
    private boolean medalApplied;

    // Message sent for this month result (congrats for top 3, result message for others)
    @Column(name = "notify_sent", nullable = false)
    private boolean notifySent;

    // Processing complete marker
    // For non winners we set this true together with notifySent
    // For top 3 we set this true only when reward succeeds
    @Column(name = "reward_granted", nullable = false)
    private boolean rewardGranted;

    // Lightweight lock for cross proxy processing
    @Column(name = "processing", nullable = false)
    private boolean processing;

    // Epoch millis when processing lock was acquired, used to recover from crashes
    @Column(name = "processing_at", nullable = false)
    private long processingAt;

    public PlayerVoteMonthlyEntity() {
    }

    public PlayerVoteMonthlyEntity(long playerId, int monthYearMonth) {
        this.id = new PlayerVoteMonthlyKey(playerId, monthYearMonth);
        this.monthVotes = 0;

        this.finalRank = 0;
        this.medalApplied = false;

        this.notifySent = false;
        this.rewardGranted = false;

        this.processing = false;
        this.processingAt = 0L;
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

    public int getFinalRank() {
        return finalRank;
    }

    public void setFinalRank(int finalRank) {
        this.finalRank = finalRank;
    }

    public boolean isMedalApplied() {
        return medalApplied;
    }

    public void setMedalApplied(boolean medalApplied) {
        this.medalApplied = medalApplied;
    }

    public boolean isNotifySent() {
        return notifySent;
    }

    public void setNotifySent(boolean notifySent) {
        this.notifySent = notifySent;
    }

    public boolean isRewardGranted() {
        return rewardGranted;
    }

    public void setRewardGranted(boolean rewardGranted) {
        this.rewardGranted = rewardGranted;
    }

    public boolean isProcessing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

    public long getProcessingAt() {
        return processingAt;
    }

    public void setProcessingAt(long processingAt) {
        this.processingAt = processingAt;
    }
}