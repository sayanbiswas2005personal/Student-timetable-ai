package com.college.timetable.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableStatus;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    Optional<Timetable> findFirstBySectionIdOrderByVersionDesc(Long sectionId);

    @Query("select max(t.version) from Timetable t where t.section.id = :sectionId")
    Integer findMaxVersion(@Param("sectionId") Long sectionId);

    @Query("""
            select t from Timetable t
            where t.section.id = :sectionId and t.status = :status
            order by t.version desc
            """)
    List<Timetable> findBySectionAndStatus(@Param("sectionId") Long sectionId,
                                           @Param("status") TimetableStatus status);

    /**
     * Published versions that are in force on {@code date}. Ordering by version descending lets the
     * caller take the first row as the authoritative version; drafts and under review rows are
     * excluded by the status predicate so a newer draft can never shadow a published timetable.
     */
    @Query("""
            select t from Timetable t
            where t.section.id = :sectionId
              and t.status = com.college.timetable.entity.TimetableStatus.PUBLISHED
              and t.effectiveFrom <= :date
              and (t.effectiveTo is null or t.effectiveTo >= :date)
            order by t.version desc
            """)
    List<Timetable> findEffectivePublished(@Param("sectionId") Long sectionId, @Param("date") LocalDate date);

    @Query("""
            select t from Timetable t
            where t.section.id = :sectionId and t.status = :status
            order by t.version desc
            """)
    List<Timetable> findAllVersions(@Param("sectionId") Long sectionId,
                                    @Param("status") TimetableStatus status);

    List<Timetable> findByStatusOrderByCreatedAtDesc(TimetableStatus status);

    @Query("""
            select t from Timetable t
            where t.section.id = :sectionId
              and t.status in (com.college.timetable.entity.TimetableStatus.DRAFT,
                               com.college.timetable.entity.TimetableStatus.UNDER_REVIEW)
              and t.effectiveFrom <= :date
              and (t.effectiveTo is null or t.effectiveTo >= :date)
            order by t.version desc
            """)
    List<Timetable> findEffectiveUnpublished(@Param("sectionId") Long sectionId, @Param("date") LocalDate date);

    @Query("""
            select t from Timetable t
            join fetch t.section s
            join fetch s.program p
            where t.id = :id
            """)
    Optional<Timetable> findByIdWithSection(@Param("id") Long id);
}