package com.college.timetable.dto.timetable;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PublishTimetableRequest",
        description = "Approve a version for use. Optional effective dates let an administrator "
                + "backdate a version, for example when fixing a wrong week of data.")
public record PublishTimetableRequest(LocalDate effectiveFrom, LocalDate effectiveTo) {
}
