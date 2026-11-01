package de.rechnio.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LetterheadTest {

    @Test
    void wideLogoUsesFullWidth() {
        // 600 x 100 px: Breite begrenzt (60 mm), Hoehe 10 mm
        assertThat(Letterhead.logoStyle(600, 100)).isEqualTo("width: 60.0mm; height: 10.0mm;");
    }

    @Test
    void ratioDecidesWhichEdgeLimits() {
        // 480 x 140 px (3,4 : 1) ist breiter als der Kasten (3 : 1): Breite begrenzt
        assertThat(Letterhead.logoStyle(480, 140)).isEqualTo("width: 60.0mm; height: 17.5mm;");
        // quadratisch: Hoehe begrenzt
        assertThat(Letterhead.logoStyle(200, 200)).isEqualTo("width: 20.0mm; height: 20.0mm;");
    }
}
