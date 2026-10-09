package com.college.timetable.service.importreview;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.college.timetable.service.importreview.TimetableBootstrapRunner.Row;

class TimetableBootstrapFurnitureTest {

    private Row row(String subjectLabel, String rawText) {
        return new Row(1, "B.TECH CSE III A", 1, "14:30", "15:25",
                subjectLabel, null, null, null, null, rawText);
    }

    @Test
    @DisplayName("the generated-on footer is removed without losing the class before it")
    void removesGeneratedFooter() {
        Row cleaned = TimetableBootstrapRunner.stripFurniture(
                row("Data Warehousing & Data Analytics generated: 1/9/2026",
                        "Data Warehousing & Data Analytics (CSE21963) Dr. Suman Bhattacharjee generated: 1/9/2026"));

        assertThat(cleaned.subjectLabel()).isEqualTo("Data Warehousing & Data Analytics");
        assertThat(cleaned.rawText())
                .isEqualTo("Data Warehousing & Data Analytics (CSE21963) Dr. Suman Bhattacharjee");
    }

    @Test
    @DisplayName("the timetable website watermark is removed from a merged cell")
    void removesWatermark() {
        Row cleaned = TimetableBootstrapRunner.stripFurniture(
                row("Professional Core – I Principles of Programming Language aSc Timetables Online",
                        "Professional Core – I Principles of Programming Language (CSE11103) aSc Timetables Online"));

        assertThat(cleaned.subjectLabel())
                .isEqualTo("Professional Core – I Principles of Programming Language");
        assertThat(cleaned.rawText())
                .isEqualTo("Professional Core – I Principles of Programming Language (CSE11103)");
    }

    @Test
    @DisplayName("the timestamp's digits survive, because only the label and not the code is touched")
    void keepsSubjectCode() {
        Row cleaned = TimetableBootstrapRunner.stripFurniture(
                row("SEC5-Data Analysis with Excell generated: 1/9/2026",
                        "SEC5-Data Analysis with Excell(SEC170) generated: 1/9/2026"));

        assertThat(cleaned.rawText()).isEqualTo("SEC5-Data Analysis with Excell(SEC170)");
        assertThat(cleaned.subjectCode()).isNull();
    }

    @Test
    @DisplayName("a cell with no marker is returned unchanged")
    void leavesRealCellsAlone() {
        Row original = row("Engineering Mathematics - III C(SDS11510)",
                "Engineering Mathematics - III C(SDS11510)");

        Row cleaned = TimetableBootstrapRunner.stripFurniture(original);

        assertThat(cleaned.subjectLabel()).isEqualTo(original.subjectLabel());
        assertThat(cleaned.rawText()).isEqualTo(original.rawText());
    }

    @Test
    @DisplayName("a footer is stripped from a cell that has no subject label at all")
    void stripsFooterWhenThereIsNoSubjectLabel() {
        Row cleaned = TimetableBootstrapRunner.stripFurniture(
                new Row(7, "B.TECH CSE V J", 5, "16:30", "17:25",
                        null, "MINOR_CSE14050", null, null, null,
                        "MINOR_CSE14050 aSc Timetables Online"));

        assertThat(cleaned.rawText()).isEqualTo("MINOR_CSE14050");
        assertThat(cleaned.subjectLabel()).isNull();
    }

    @Test
    @DisplayName("a cell that was only a footer becomes empty rather than keeping the marker")
    void emptiesFooterOnlyCell() {
        Row cleaned = TimetableBootstrapRunner.stripFurniture(
                row("generated: 1/9/2026", "generated: 1/9/2026"));

        assertThat(cleaned.subjectLabel()).isNull();
        assertThat(cleaned.rawText()).isEmpty();
    }
}