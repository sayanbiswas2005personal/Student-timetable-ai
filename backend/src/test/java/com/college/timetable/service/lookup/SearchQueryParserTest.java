package com.college.timetable.service.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SearchQueryParserTest {

    @ParameterizedTest
    @CsvSource({
            "'B.Tech CSE AI-ML, semester 5, section D', 5, D, BTechCSEAIML",
            "'B.Tech CSE, 5th semester, section D',      5, D, BTechCSE",
            "'b tech cse fifth semester sec b',          5, B, BTechCSE"
    })
    @DisplayName("reads the common course descriptions")
    void parsesTypicalInput(String text, int semester, String section, String programHint) {
        var parsed = SearchQueryParser.parse(text, null);

        assertThat(parsed.semesterNumber()).isEqualTo(semester);
        assertThat(parsed.sectionLabel()).isEqualToIgnoringCase(section);
        assertThat(parsed.programHint().replaceAll("[^A-Za-z0-9]", ""))
                .containsIgnoringCase(programHint.replaceAll("[^A-Za-z0-9]", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth",
            "ninth", "tenth", "eleventh", "twelfth"})
    @DisplayName("semester can be written as a word")
    void parsesNumberWords(String word) {
        var parsed = SearchQueryParser.parse("B.Tech CSE " + word + " semester section A", null);

        assertThat(parsed.semesterNumber()).isBetween(1, 12);
        assertThat(parsed.sectionLabel()).isEqualTo("A");
    }

    @Test
    @DisplayName("academic year forms are all understood")
    void parsesAcademicYear() {
        assertThat(SearchQueryParser.parse("B.Tech CSE 5th semester section A 2025", null).academicYear())
                .isEqualTo("2025");
        assertThat(SearchQueryParser.parse("B.Tech CSE 5th semester section A 2025-26", null).academicYear())
                .isEqualTo("2025-26");
        assertThat(SearchQueryParser.parse("B.Tech CSE 5th semester section A 2025/26", null).academicYear())
                .isEqualTo("2025-26");
    }

    @Test
    @DisplayName("a bare letter is never treated as a section")
    void bareLetterIsNotASection() {
        var parsed = SearchQueryParser.parse("B.Tech CSE AI-ML D", null);

        assertThat(parsed.sectionLabel()).isNull();
        assertThat(parsed.semesterNumber()).isNull();
        assertThat(parsed.programHint()).isNotBlank();
    }

    @Test
    @DisplayName("a semester number is only read near the word semester")
    void isolatedNumberIsNotASemester() {
        var parsed = SearchQueryParser.parse("B.Tech CSE 5 section D", null);

        assertThat(parsed.semesterNumber()).isNull();
    }

    @Test
    @DisplayName("a slash separated registration number is recognised")
    void detectsRegistrationNumber() {
        var parsed = SearchQueryParser.parse("UG/02/BTCSEAIML/2023/024", null);

        assertThat(parsed.registrationNumber()).isEqualTo("UG/02/BTCSEAIML/2023/024");
    }

    @Test
    @DisplayName("a registration number passed separately wins over the free text")
    void explicitRegistrationWins() {
        var parsed = SearchQueryParser.parse("B.Tech CSE AI-ML", "UG/02/BTCSEAIML/2023/024");

        assertThat(parsed.registrationNumber()).isEqualTo("UG/02/BTCSEAIML/2023/024");
    }

    @Test
    @DisplayName("out of range semester numbers are ignored rather than clamped")
    void rejectsImpossibleSemester() {
        assertThat(SearchQueryParser.parse("B.Tech CSE semester 13", null).semesterNumber()).isNull();
        assertThat(SearchQueryParser.parse("B.Tech CSE semester 0", null).semesterNumber()).isNull();
    }

    @Test
    @DisplayName("empty input produces an empty query instead of throwing")
    void emptyInput() {
        var parsed = SearchQueryParser.parse("   ", null);

        assertThat(parsed.raw()).isEmpty();
        assertThat(parsed.semesterNumber()).isNull();
        assertThat(parsed.sectionLabel()).isNull();
    }
}