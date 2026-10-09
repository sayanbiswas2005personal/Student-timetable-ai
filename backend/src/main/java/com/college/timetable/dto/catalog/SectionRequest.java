package com.college.timetable.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionRequest", description = "Create or update a section")
public record SectionRequest(
        @NotNull Long programId,
        @NotNull Long academicTermId,
        @NotBlank @Size(max = 20) String sectionName,
        Boolean active) {
}
