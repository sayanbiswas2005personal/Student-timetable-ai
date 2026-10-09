package com.college.timetable.repository;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.TimetableEntry;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

    List<TimetableEntry> findByTimetableIdOrderByDayOfWeekAscStartTimeAsc(Long timetableId);

    List<TimetableEntry> findByTimetableIdAndDayOfWeekOrderByStartTimeAsc(Long timetableId, int dayOfWeek);

    void deleteByTimetableId(Long timetableId);

    /**
     * Entries on the given day that share at least one instant with [start, end).
     * Overlap is expressed with strict inequality on both ends so two back to back periods
     * (10:00-11:00 and 11:00-12:00) are not reported as conflicting.
     */
    @Query("""
            select e from TimetableEntry e
            where e.timetable.id = :timetableId
              and e.dayOfWeek = :dayOfWeek
              and e.startTime < :end
              and e.endTime > :start
            order by e.startTime asc
            """)
    List<TimetableEntry> findOverlapping(@Param("timetableId") Long timetableId,
                                         @Param("dayOfWeek") int dayOfWeek,
                                         @Param("start") LocalTime start,
                                         @Param("end") LocalTime end);

    long countByTimetableId(Long timetableId);

    @Query("""
            select e from TimetableEntry e
            join fetch e.subject s
            left join fetch e.faculty f
            left join fetch e.room r
            where e.timetable.id = :timetableId and e.dayOfWeek = :dayOfWeek
            order by e.startTime asc
            """)
    List<TimetableEntry> findDayWithRelations(@Param("timetableId") Long timetableId,
                                              @Param("dayOfWeek") int dayOfWeek);

    @Query("""
            select e from TimetableEntry e
            join fetch e.subject s
            left join fetch e.faculty f
            left join fetch e.room r
            where e.timetable.id = :timetableId
            order by e.dayOfWeek asc, e.startTime asc
            """)
    List<TimetableEntry> findAllWithRelations(@Param("timetableId") Long timetableId);
}