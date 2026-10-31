package de.rechnio.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Lueckenloser Nummernkreis je Organisation, Belegart und Jahr (§14 UStG). */
@Entity
@Table(name = "number_sequence")
public class NumberSequence {

    @Embeddable
    public record Key(UUID organizationId, String docType, int docYear) implements Serializable {
    }

    @EmbeddedId
    private Key id;
    private int lastValue;

    protected NumberSequence() {
    }

    public NumberSequence(Key id) {
        this.id = id;
        this.lastValue = 0;
    }

    public int next() {
        return ++lastValue;
    }
}
