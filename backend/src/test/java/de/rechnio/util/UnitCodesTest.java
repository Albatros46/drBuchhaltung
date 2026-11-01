package de.rechnio.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnitCodesTest {

    @Test
    void mapsGermanUnitsToCodes() {
        assertThat(UnitCodes.of("m²")).isEqualTo("MTK");
        assertThat(UnitCodes.of(" Std ")).isEqualTo("HUR");
        assertThat(UnitCodes.of("Pauschale")).isEqualTo("LS");
        assertThat(UnitCodes.of("Monat")).isEqualTo("MON");
        assertThat(UnitCodes.of("Sack")).isEqualTo(UnitCodes.DEFAULT);
        assertThat(UnitCodes.of(null)).isEqualTo(UnitCodes.DEFAULT);
    }

    @Test
    void labelsCodesFromIncomingInvoices() {
        assertThat(UnitCodes.label("MTK")).isEqualTo("m²");
        assertThat(UnitCodes.label("c62")).isEqualTo("Einh.");
        assertThat(UnitCodes.label("LS")).isEqualTo("Pauschale");
        assertThat(UnitCodes.label("XYZ")).as("unbekannt bleibt erhalten").isEqualTo("XYZ");
        assertThat(UnitCodes.label(null)).isNull();
    }

    @Test
    void everyCodeWeWriteHasALabel() {
        for (String unit : new String[] {"Stk", "Std", "Min", "Tag", "Woche", "Monat", "m", "m2", "m3", "kg", "t", "l",
                "km", "Pauschale"}) {
            String code = UnitCodes.of(unit);
            assertThat(UnitCodes.label(code)).as(unit).isNotEqualTo(code);
        }
    }
}
