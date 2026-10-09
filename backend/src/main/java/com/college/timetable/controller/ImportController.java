package com.college.timetable.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.college.timetable.dto.imports.ApproveImportRequest;
import com.college.timetable.dto.imports.ImportCandidateDto;
import com.college.timetable.dto.imports.ImportJobDto;
import com.college.timetable.dto.imports.UpdateCandidateRequest;
import com.college.timetable.dto.timetable.PublishTimetableRequest;
import com.college.timetable.service.importreview.ImportService;
import com.college.timetable.service.timetable.TimetableService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Administrator only timetable import. Every endpoint in this controller is restricted to the
 * ADMIN role by the URL rules in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/imports")
@Tag(name = "PDF import")
public class ImportController {

    private final ImportService importService;
    private final TimetableService timetableService;

    public ImportController(ImportService importService, TimetableService timetableService) {
        this.importService = importService;
        this.timetableService = timetableService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload a timetable PDF, extract it and produce reviewable rows")
    public ResponseEntity<ImportJobDto> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importService.upload(file));
    }

    @GetMapping
    @Operation(summary = "Import history, newest first")
    public List<ImportJobDto> list() {
        return importService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Status of one import job")
    public ImportJobDto get(@PathVariable Long id) {
        return importService.get(id);
    }

    @GetMapping("/{id}/candidates")
    @Operation(summary = "Every extracted row for review")
    public List<ImportCandidateDto> candidates(@PathVariable Long id) {
        return importService.candidates(id);
    }

    @PutMapping("/{id}/candidates/{candidateId}")
    @Operation(summary = "Correct or accept one extracted row")
    public ImportCandidateDto updateCandidate(@PathVariable Long id, @PathVariable Long candidateId,
                                              @Valid @RequestBody UpdateCandidateRequest request) {
        return importService.updateCandidate(id, candidateId, request);
    }

    @DeleteMapping("/{id}/candidates/{candidateId}")
    @Operation(summary = "Discard one extracted row")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id, @PathVariable Long candidateId) {
        importService.deleteCandidate(id, candidateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Create a draft timetable from the approved rows. Still not published.")
    public ResponseEntity<TimetableApprovalResponse> approve(@PathVariable Long id,
                                                             @Valid @RequestBody ApproveImportRequest request) {
        Long timetableId = importService.approve(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TimetableApprovalResponse(timetableId, "TIMETABLE",
                        "Draft timetable created. Review it, then publish it when it is correct."));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish the timetable created by an approved import")
    public com.college.timetable.dto.timetable.TimetableDto publish(
            @PathVariable Long id,
            @RequestParam Long timetableId,
            @RequestBody(required = false) PublishTimetableRequest request) {
        if (importService.get(id).resultTimetableId() == null
                || !importService.get(id).resultTimetableId().equals(timetableId)) {
            throw com.college.timetable.exception.ApiException.badRequest("TIMETABLE_NOT_FROM_THIS_IMPORT",
                    "That timetable was not produced by this import job.");
        }
        return timetableService.publish(timetableId, request);
    }

    /** The approval step never publishes. It only creates a draft that still needs review. */
    public record TimetableApprovalResponse(Long timetableId, String kind, String message) {
    }
}