package com.college.timetable.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeTextTest {

    @ParameterizedTest
    @CsvSource({
            "'9:30', 9, 30",
            "'09:30', 9, 30",
            "'9.30', 9, 30",
            "'0930', 9, 30",
            "'9:30 AM', 9, 30",
            "'1:05 pm', 13, 5",
            "'12:00 AM', 0, 0",
            "'12:00 PM', 12, 0"
    })
    @DisplayName("reads the formats colleges actually print")
    void parsesCommonFormats(String text, int hour, int minute) {
        assertThat(TimeText.parse(text)).isEqualTo(LocalTime.of(hour, minute));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "25:00", "10:75", "13 pm", "-"})
    @DisplayName("unreadable text returns null rather than a guess")
    void returnsNullForInvalidText(String text) {
        assertThat(TimeText.parse(text)).isNull();
    }

    @Test
    @DisplayName("null is handled")
    void nullInput() {
        assertThat(TimeText.parse(null)).isNull();
        assertThat(TimeText.format(null)).isNull();
    }

    @Test
    @DisplayName("always writes HH:mm")
    void formatsConsistently() {
        assertThat(TimeText.format(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(TimeText.format(LocalTime.of(16, 30))).isEqualTo("16:30");
        assertThat(TimeText.format(LocalTime.NOON)).isEqualTo("12:00");
    }

    @Test
    @DisplayName("parseRequired rejects unreadable input with a helpful message")
    void parseRequiredThrows() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TimeText.parseRequired("noon", "seed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noon");
    }
}