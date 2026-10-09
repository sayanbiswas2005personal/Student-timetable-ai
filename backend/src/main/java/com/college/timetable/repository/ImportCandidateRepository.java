package com.college.timetable.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.timetable.entity.ImportCandidate;
import com.college.timetable.entity.ReviewStatus;

public interface ImportCandidateRepository extends JpaRepository<ImportCandidate, Long> {

    List<ImportCandidate> findByImportJobIdOrderByPageNumberAscIdAsc(Long importJobId);

    List<ImportCandidate> findByImportJobIdAndReviewStatusOrderByPageNumberAscIdAsc(Long importJobId,
                                                                                    ReviewStatus reviewStatus);

    long countByImportJobId(Long importJobId);

    long countByImportJobIdAndReviewStatus(Long importJobId, ReviewStatus reviewStatus);

    void deleteByImportJobId(Long importJobId);
}