package de.rechnio.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.rechnio.entity.IncomingInvoice;
import de.rechnio.enums.EInvoiceFormat;

class DatevIncomingSplitTest {

    private static IncomingInvoice invoice(String net, String vat, String gross) {
        IncomingInvoice inv = new IncomingInvoice(UUID.randomUUID(), EInvoiceFormat.CII, "x.xml", "sha");
        inv.totals(new BigDecimal(net), new BigDecimal(vat), new BigDecimal(gross));
        return inv;
    }

    private static void line(IncomingInvoice inv, String net, String rate) {
        inv.addLine("Pos", BigDecimal.ONE, "C62", new BigDecimal(net), rate == null ? null : new BigDecimal(rate),
                new BigDecimal(net));
    }

    @Test
    void splitsByRateAndMatchesSupplierTotal() {
        // 100,00 zu 19 % und 50,00 zu 7 %: 119,00 + 53,50 = 172,50
        IncomingInvoice inv = invoice("150.00", "22.50", "172.50");
        line(inv, "100.00", "19");
        line(inv, "50.00", "7");

        var parts = DatevExportServiceImpl.grossByRate(inv);

        assertThat(parts).extracting(p -> p.rate().toPlainString(), p -> p.gross().toPlainString())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("7", "53.50"),
                        org.assertj.core.groups.Tuple.tuple("19", "119.00"));
    }

    @Test
    void roundingDifferenceGoesToLargestGroup() {
        // Lieferant rundet je Position: Summe 23,81 statt rechnerisch 23,80
        IncomingInvoice inv = invoice("20.00", "3.81", "23.81");
        line(inv, "10.00", "19");
        line(inv, "10.00", "19");

        var parts = DatevExportServiceImpl.grossByRate(inv);

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst().gross()).isEqualByComparingTo("23.81");
    }

    @Test
    void withoutLinesTheRateIsDerivedFromTotals() {
        IncomingInvoice inv = invoice("100.00", "7.00", "107.00");

        var parts = DatevExportServiceImpl.grossByRate(inv);

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst().rate()).isEqualByComparingTo("7");
        assertThat(parts.getFirst().gross()).isEqualByComparingTo("107.00");
    }

    @Test
    void belegfeldKeepsOnlyAllowedCharacters() {
        assertThat(DatevExportServiceImpl.belegfeld("RE 2026/0042_ä")).isEqualTo("RE2026/0042");
        assertThat(DatevExportServiceImpl.belegfeld("X".repeat(50))).hasSize(36);
        assertThat(DatevExportServiceImpl.belegfeld(null)).isEmpty();
    }
}
