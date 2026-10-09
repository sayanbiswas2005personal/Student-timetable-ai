package com.college.timetable.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SectionLabelParserTest {

    @ParameterizedTest
    @CsvSource({
            // Longest numeral must win: "III" is not "I" plus section "II A".
            "B.TECH CSE III A,  B.TECH CSE, 3, A",
            "B.TECH CSE II A,   B.TECH CSE, 2, A",
            "B.TECH CSE VII A,  B.TECH CSE, 7, A",
            "B.TECH CSE VIII A, B.TECH CSE, 8, A",
            "B.TECH CSE IX A,   B.TECH CSE, 9, A",
            "B.TECH CSE X A,    B.TECH CSE, 10, A",
            "B.TECH CSE XI A,   B.TECH CSE, 11, A",
            "B.TECH CSE XII A,  B.TECH CSE, 12, A",
            "B.TECH CSE I A,    B.TECH CSE, 1, A",
            "B.TECH CSE IV A,   B.TECH CSE, 4, A",
            "B.TECH CSE V A,    B.TECH CSE, 5, A",
            "B.TECH CSE VI A,   B.TECH CSE, 6, A",
    })
    void readsProgrammeSemesterAndSection(String label, String program, int semester, String section) {
        var parsed = SectionLabelParser.parse(label);

        assertThat(parsed).isNotNull();
        assertThat(parsed.program()).isEqualTo(program);
        assertThat(parsed.semester()).isEqualTo(semester);
        assertThat(parsed.section()).isEqualTo(section);
    }

    @Test
    void keepsQualifiersWithTheSectionName() {
        var parsed = SectionLabelParser.parse("B.TECH CSE III B (BFSI)");

        assertThat(parsed).isNotNull();
        assertThat(parsed.semester()).isEqualTo(3);
        assertThat(parsed.section()).isEqualTo("B (BFSI)");
    }

    @Test
    void usesMainWhenNoSectionLetterIsPrinted() {
        var parsed = SectionLabelParser.parse("M.TECH DSDT I");

        assertThat(parsed).isNotNull();
        assertThat(parsed.program()).isEqualTo("M.TECH DSDT");
        assertThat(parsed.semester()).isEqualTo(1);
        assertThat(parsed.section()).isEqualTo("MAIN");
    }

    @Test
    void collapsesRepeatedWhitespace() {
        var parsed = SectionLabelParser.parse("  B.TECH   CSE   V   A  ");

        assertThat(parsed).isNotNull();
        assertThat(parsed.rawLabel()).isEqualTo("B.TECH CSE V A");
        assertThat(parsed.program()).isEqualTo("B.TECH CSE");
        assertThat(parsed.section()).isEqualTo("A");
    }

    @Test
    void refusesToGuessWhenTheSemesterIsMissing() {
        assertThat(SectionLabelParser.parse("B.TECH CSE A")).isNull();
        assertThat(SectionLabelParser.parse("B.TECH CSE")).isNull();
        assertThat(SectionLabelParser.parse("")).isNull();
        assertThat(SectionLabelParser.parse(null)).isNull();
    }

    @Test
    void derivesAStableProgrammeCode() {
        assertThat(SectionLabelParser.programCode("B.TECH CSE")).isEqualTo("BTECHCSE");
        assertThat(SectionLabelParser.programCode("M.TECH DSDT")).isEqualTo("MTECHDSDT");
    }
}