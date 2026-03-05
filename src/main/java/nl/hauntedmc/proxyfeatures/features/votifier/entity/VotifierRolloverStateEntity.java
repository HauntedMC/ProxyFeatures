package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "votifier_rollover_state")
public class VotifierRolloverStateEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "last_reset_yyyymm", nullable = false)
    private int lastResetYearMonth;

    public VotifierRolloverStateEntity() {
    }

    public VotifierRolloverStateEntity(int id, int lastResetYearMonth) {
        this.id = id;
        this.lastResetYearMonth = lastResetYearMonth;
    }

    public Integer getId() {
        return id;
    }

    public int getLastResetYearMonth() {
        return lastResetYearMonth;
    }

    public void setLastResetYearMonth(int lastResetYearMonth) {
        this.lastResetYearMonth = lastResetYearMonth;
    }
}