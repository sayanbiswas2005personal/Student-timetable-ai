package com.college.timetable.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.college.timetable.dto.timetable.CreateTimetableRequest;
import com.college.timetable.dto.timetable.PublishTimetableRequest;
import com.college.timetable.dto.timetable.TimetableDto;
import com.college.timetable.dto.timetable.TimetableEntryDto;
import com.college.timetable.dto.timetable.TimetableEntryRequest;
import com.college.timetable.dto.timetable.TimetableValidationReport;
import com.college.timetable.service.timetable.TimetableService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/timetables")
@Tag(name = "Timetable management")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping
    @Operation(summary = "List timetable versions, optionally filtered by section")
    public List<TimetableDto> list(@RequestParam(required = false) Long sectionId) {
        return timetableService.list(sectionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One timetable version with all its periods")
    public TimetableDto get(@PathVariable Long id) {
        return timetableService.get(id);
    }

    @GetMapping("/{id}/validate")
    @Operation(summary = "Check whether a version is ready to publish")
    public TimetableValidationReport validate(@PathVariable Long id) {
        return timetableService.validate(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new draft version for a section")
    public ResponseEntity<TimetableDto> create(@Valid @RequestBody CreateTimetableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timetableService.createDraft(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update effective dates of a draft version")
    public TimetableDto update(@PathVariable Long id, @Valid @RequestBody CreateTimetableRequest request) {
        return timetableService.update(id, request);
    }

    @PostMapping("/{id}/entries")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a period")
    public ResponseEntity<TimetableEntryDto> addEntry(@PathVariable Long id,
                                                      @Valid @RequestBody TimetableEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timetableService.upsertEntry(id, null, request));
    }

    @PutMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a period")
    public TimetableEntryDto updateEntry(@PathVariable Long id, @PathVariable Long entryId,
                                         @Valid @RequestBody TimetableEntryRequest request) {
        return timetableService.upsertEntry(id, entryId, request);
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove a period")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id, @PathVariable Long entryId) {
        timetableService.deleteEntry(id, entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a version for use by the lookup engine")
    public TimetableDto publish(@PathVariable Long id,
                                @RequestBody(required = false) PublishTimetableRequest request) {
        return timetableService.publish(id, request);
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore an earlier published version")
    public TimetableDto rollback(@PathVariable Long id,
                                 @RequestBody(required = false) PublishTimetableRequest request) {
        return timetableService.rollback(id, request);
    }
}