package de.rechnio.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.rechnio.entity.Invoice;
import de.rechnio.enums.DocType;
import de.rechnio.enums.TaxMode;

class VatCalculatorTest {

    private static Invoice invoice(TaxMode mode) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), DocType.RECHNUNG, mode);
    }

    private static void add(Invoice inv, String qty, String price, String rate) {
        inv.addLine("Pos", new BigDecimal(qty), "Stk", new BigDecimal(price), new BigDecimal(rate));
    }

    @Test
    void groupsByRateAndRoundsPerGroup() {
        Invoice inv = invoice(TaxMode.STANDARD);
        add(inv, "8", "85.00", "19");   // 680,00
        add(inv, "1", "45.00", "19");   // 45,00
        add(inv, "3", "3.33", "7");     // 9,99

        assertThat(inv.getNetTotal()).isEqualByComparingTo("734.99");
        // 725,00 * 19 % = 137,75 ; 9,99 * 7 % = 0,6993 -> 0,70
        assertThat(inv.getVatTotal()).isEqualByComparingTo("138.45");
        assertThat(inv.getGrossTotal()).isEqualByComparingTo("873.44");
        assertThat(VatCalculator.totals(inv.getLines()).groups()).hasSize(2);
    }

    @Test
    void lineNetIsRoundedHalfUp() {
        assertThat(VatCalculator.lineNet(new BigDecimal("1.5"), new BigDecimal("0.33"))).isEqualByComparingTo("0.50");
        assertThat(VatCalculator.lineNet(new BigDecimal("2.125"), new BigDecimal("10.00"))).isEqualByComparingTo("21.25");
    }

    @Test
    void kleinunternehmerAndReverseChargeShowNoVat() {
        Invoice klein = invoice(TaxMode.KLEINUNTERNEHMER);
        add(klein, "1", "100.00", "19");
        Invoice rc = invoice(TaxMode.REVERSE_CHARGE);
        add(rc, "1", "100.00", "19");

        assertThat(klein.getVatTotal()).isEqualByComparingTo("0");
        assertThat(klein.getGrossTotal()).isEqualByComparingTo("100.00");
        assertThat(rc.getVatTotal()).isEqualByComparingTo("0");
        assertThat(rc.getLines().getFirst().getVatRate()).isEqualByComparingTo("0");
    }
}
