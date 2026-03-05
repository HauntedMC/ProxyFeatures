package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(
        name = "player_vote_stats",
        indexes = {
                @Index(name = "idx_vote_month", columnList = "month_year_month"),
                @Index(name = "idx_vote_month_votes", columnList = "month_year_month, month_votes"),
                @Index(name = "idx_vote_medals", columnList = "first_places, second_places, third_places"),
                @Index(name = "idx_vote_remind_enabled", columnList = "vote_remind_enabled")
        }
)
public class PlayerVoteStatsEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Column(name = "month_year_month", nullable = false)
    private int monthYearMonth;

    @Column(name = "month_votes", nullable = false)
    private int monthVotes;

    @Column(name = "total_votes", nullable = false)
    private long totalVotes;

    @Column(name = "highest_month_votes", nullable = false)
    private int highestMonthVotes;

    @Column(name = "last_vote_at", nullable = false)
    private long lastVoteAt;

    @Column(name = "last_vote_service", length = 64, nullable = false)
    private String lastVoteService = "";

    @Column(name = "last_vote_address", length = 64, nullable = false)
    private String lastVoteAddress = "";

    @Column(name = "vote_streak", nullable = false)
    private int voteStreak;

    @Column(name = "best_vote_streak", nullable = false)
    private int bestVoteStreak;

    // Vote reminders (default on)
    @Column(name = "vote_remind_enabled", nullable = false)
    private boolean voteRemindEnabled = true;

    // Lifetime medals
    @Column(name = "first_places", nullable = false)
    private int firstPlaces;

    @Column(name = "second_places", nullable = false)
    private int secondPlaces;

    @Column(name = "third_places", nullable = false)
    private int thirdPlaces;

    public PlayerVoteStatsEntity() {
    }

    public PlayerVoteStatsEntity(PlayerEntity player) {
        this.player = player;
        this.playerId = player.getId();
        this.monthYearMonth = 0;
        this.monthVotes = 0;
        this.totalVotes = 0;
        this.highestMonthVotes = 0;
        this.lastVoteAt = 0;
        this.voteStreak = 0;
        this.bestVoteStreak = 0;

        this.voteRemindEnabled = true;

        this.firstPlaces = 0;
        this.secondPlaces = 0;
        this.thirdPlaces = 0;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public int getMonthYearMonth() {
        return monthYearMonth;
    }

    public void setMonthYearMonth(int monthYearMonth) {
        this.monthYearMonth = monthYearMonth;
    }

    public int getMonthVotes() {
        return monthVotes;
    }

    public void setMonthVotes(int monthVotes) {
        this.monthVotes = monthVotes;
    }

    public long getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(long totalVotes) {
        this.totalVotes = totalVotes;
    }

    public int getHighestMonthVotes() {
        return highestMonthVotes;
    }

    public void setHighestMonthVotes(int highestMonthVotes) {
        this.highestMonthVotes = highestMonthVotes;
    }

    public long getLastVoteAt() {
        return lastVoteAt;
    }

    public void setLastVoteAt(long lastVoteAt) {
        this.lastVoteAt = lastVoteAt;
    }

    public String getLastVoteService() {
        return lastVoteService;
    }

    public void setLastVoteService(String lastVoteService) {
        this.lastVoteService = lastVoteService == null ? "" : lastVoteService;
    }

    public String getLastVoteAddress() {
        return lastVoteAddress;
    }

    public void setLastVoteAddress(String lastVoteAddress) {
        this.lastVoteAddress = lastVoteAddress == null ? "" : lastVoteAddress;
    }

    public int getVoteStreak() {
        return voteStreak;
    }

    public void setVoteStreak(int voteStreak) {
        this.voteStreak = voteStreak;
    }

    public int getBestVoteStreak() {
        return bestVoteStreak;
    }

    public void setBestVoteStreak(int bestVoteStreak) {
        this.bestVoteStreak = bestVoteStreak;
    }

    public boolean isVoteRemindEnabled() {
        return voteRemindEnabled;
    }

    public void setVoteRemindEnabled(boolean voteRemindEnabled) {
        this.voteRemindEnabled = voteRemindEnabled;
    }

    public int getFirstPlaces() {
        return firstPlaces;
    }

    public void setFirstPlaces(int firstPlaces) {
        this.firstPlaces = firstPlaces;
    }

    public int getSecondPlaces() {
        return secondPlaces;
    }

    public void setSecondPlaces(int secondPlaces) {
        this.secondPlaces = secondPlaces;
    }

    public int getThirdPlaces() {
        return thirdPlaces;
    }

    public void setThirdPlaces(int thirdPlaces) {
        this.thirdPlaces = thirdPlaces;
    }
}