package com.college.timetable.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CellContentParserTest {

    private final CellContentParser parser = new CellContentParser();

    @Test
    @DisplayName("a subject with its code and a faculty with a staff code is split into parts")
    void parsesSubjectCodeFacultyAndRoom() {
        var parsed = parser.parse("Cloud Computing (CSE11036) Prof. (Dr.) Amitava Sen (56312) AU6-4304");

        assertThat(parsed.subjectName()).isEqualToIgnoringCase("Cloud Computing");
        assertThat(parsed.subjectCode()).isEqualTo("CSE11036");
        assertThat(parsed.facultyCode()).isEqualTo("56312");
        assertThat(parsed.facultyName()).contains("Amitava Sen");
        assertThat(parsed.roomCode()).isEqualTo("AU6-4304");
        assertThat(parsed.entryType()).isEqualTo(CellContentParser.EntryTypeHint.CLASS);
        assertThat(parsed.confidence()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("a code only cell still produces a usable subject code")
    void codeOnlyCell() {
        var parsed = parser.parse("MINOR_CSE14050");

        assertThat(parsed.subjectCode()).isEqualTo("MINOR_CSE14050");
        assertThat(parsed.confidence()).isGreaterThan(0.4);
    }

    @Test
    @DisplayName("a name only cell produces a subject name and says the code is unknown")
    void nameOnlyCell() {
        var parsed = parser.parse("Professional Elective IV Cryptography and Cyber Security");

        assertThat(parsed.subjectName()).contains("Cryptography");
        assertThat(parsed.subjectCode()).isNull();
        assertThat(parsed.unresolved()).isNotEmpty();
    }

    @Test
    @DisplayName("a break is recognised from its wording")
    void recognisesBreak() {
        var parsed = parser.parse("Lunch Break");

        assertThat(parsed.entryType()).isEqualTo(CellContentParser.EntryTypeHint.BREAK);
    }

    @Test
    @DisplayName("an empty cell is reported instead of being silently accepted")
    void emptyCell() {
        var parsed = parser.parse("   ");

        assertThat(parsed.subjectName()).isNull();
        assertThat(parsed.subjectCode()).isNull();
        assertThat(parsed.confidence()).isZero();
        assertThat(parsed.unresolved()).contains("The cell is empty.");
    }

    @Test
    @DisplayName("null input does not throw")
    void nullInput() {
        assertThat(parser.parse(null).unresolved()).isNotEmpty();
    }

    @Test
    @DisplayName("confidence stays inside zero and one even for odd input")
    void confidenceIsBounded() {
        assertThat(parser.parse("CSE11036 (56312) AU6-4304").confidence()).isLessThanOrEqualTo(1.0);
        assertThat(parser.parse("???").confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("a missing room is called out rather than filled in")
    void missingRoomIsReported() {
        var parsed = parser.parse("Cloud Computing (CSE11036)");

        assertThat(parsed.roomCode()).isNull();
        assertThat(parsed.unresolved()).anyMatch(u -> u.contains("room"));
    }
}