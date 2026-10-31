package de.rechnio.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.rechnio.entity.InvoiceLine;

/**
 * Betragsberechnung mit BigDecimal (niemals double).
 * Die Umsatzsteuer wird je Steuersatz auf die Netto-Summe gerechnet und kaufmaennisch gerundet.
 */
public final class VatCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private VatCalculator() {
    }

    public record VatGroup(BigDecimal rate, BigDecimal net, BigDecimal vat) {
        public BigDecimal gross() {
            return net.add(vat);
        }
    }

    public record Totals(BigDecimal net, BigDecimal vat, BigDecimal gross, List<VatGroup> groups) {
    }

    public static BigDecimal lineNet(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totals totals(List<InvoiceLine> lines) {
        Map<BigDecimal, BigDecimal> netByRate = new TreeMap<>();
        for (InvoiceLine line : lines) {
            netByRate.merge(line.getVatRate().stripTrailingZeros(), line.getNetAmount(), BigDecimal::add);
        }
        List<VatGroup> groups = netByRate.entrySet().stream()
                .map(e -> new VatGroup(e.getKey(), e.getValue(),
                        e.getValue().multiply(e.getKey()).divide(HUNDRED, 2, RoundingMode.HALF_UP)))
                .toList();
        BigDecimal net = groups.stream().map(VatGroup::net).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        BigDecimal vat = groups.stream().map(VatGroup::vat).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new Totals(net, vat, net.add(vat), groups);
    }
}
