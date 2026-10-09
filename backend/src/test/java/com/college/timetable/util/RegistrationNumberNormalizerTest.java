package com.college.timetable.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegistrationNumberNormalizerTest {

    @Test
    @DisplayName("null stays null")
    void nullInput() {
        assertThat(RegistrationNumberNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("blank input is detected")
    void blankInput() {
        assertThat(RegistrationNumberNormalizer.isBlank("   ")).isTrue();
        assertThat(RegistrationNumberNormalizer.isBlank("\t\n")).isTrue();
        assertThat(RegistrationNumberNormalizer.isBlank("A")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "'  UG/02/BTCSEAIML/2023/024  ', 'UG/02/BTCSEAIML/2023/024'",
            "'ug/02/btcseaiml/2023/024',  'UG/02/BTCSEAIML/2023/024'",
            "'UG/02/BTCSEAIML/2023/024',  'UG/02/BTCSEAIML/2023/024'",
            "'UG / 02 / BTCSEAIML / 2023 / 024','UG / 02 / BTCSEAIML / 2023 / 024'"
    })
    @DisplayName("case and surrounding or repeated whitespace are harmless")
    void normalisesCosmeticDifferences(String input, String expected) {
        assertThat(RegistrationNumberNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("punctuation is preserved so different students never collide")
    void punctuationIsPreserved() {
        String withSlash = RegistrationNumberNormalizer.normalize("UG/02/A/2023/1");
        String withoutSlash = RegistrationNumberNormalizer.normalize("UG02A20231");
        String longer = RegistrationNumberNormalizer.normalize("UG/02/A/2023/10");

        assertThat(withSlash).isEqualTo("UG/02/A/2023/1");
        assertThat(withoutSlash).isNotEqualTo(withSlash);
        assertThat(longer).isNotEqualTo(withSlash);
    }

    @Test
    @DisplayName("invisible characters pasted from a document are removed")
    void removesInvisibleCharacters() {
        String pasted = "UG/02/A/2023/1 ";
        assertThat(RegistrationNumberNormalizer.normalize(pasted)).isEqualTo("UG/02/A/2023/1");
    }

    @Test
    @DisplayName("normalisation is idempotent")
    void idempotent() {
        String once = RegistrationNumberNormalizer.normalize("  ug/02/a/2023/1 ");
        assertThat(RegistrationNumberNormalizer.normalize(once)).isEqualTo(once);
    }
}