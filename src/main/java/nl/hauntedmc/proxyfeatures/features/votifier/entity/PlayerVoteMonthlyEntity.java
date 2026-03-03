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
                @Index(name = "idx_vote_monthly_player", columnList = "player_id")
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

    public PlayerVoteMonthlyEntity() {
    }

    public PlayerVoteMonthlyEntity(long playerId, int monthYearMonth) {
        this.id = new PlayerVoteMonthlyKey(playerId, monthYearMonth);
        this.monthVotes = 0;
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
}