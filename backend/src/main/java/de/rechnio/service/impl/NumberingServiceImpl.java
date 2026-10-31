package de.rechnio.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.rechnio.entity.NumberSequence;
import de.rechnio.enums.DocType;
import de.rechnio.repository.NumberSequenceRepository;
import de.rechnio.service.NumberingService;

@Service
public class NumberingServiceImpl implements NumberingService {

    private final NumberSequenceRepository sequences;

    public NumberingServiceImpl(NumberSequenceRepository sequences) {
        this.sequences = sequences;
    }

    // Laeuft in der Transaktion der Ausstellung: scheitert diese, wird auch die Nummer nicht verbraucht.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID organizationId, DocType type, int year) {
        NumberSequence.Key key = new NumberSequence.Key(organizationId, type.name(), year);
        NumberSequence seq = sequences.findForUpdate(key)
                .orElseGet(() -> sequences.saveAndFlush(new NumberSequence(key)));
        return "%s-%d-%04d".formatted(type.prefix(), year, seq.next());
    }
}
