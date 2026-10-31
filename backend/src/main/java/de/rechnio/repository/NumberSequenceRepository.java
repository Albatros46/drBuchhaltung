package de.rechnio.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import de.rechnio.entity.NumberSequence;
import jakarta.persistence.LockModeType;

public interface NumberSequenceRepository extends JpaRepository<NumberSequence, NumberSequence.Key> {

    /** Sperrt die Zeile, damit parallele Ausstellungen keine doppelte oder uebersprungene Nummer erzeugen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NumberSequence s where s.id = ?1")
    Optional<NumberSequence> findForUpdate(NumberSequence.Key key);
}
