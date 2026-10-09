package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.timetable.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByRoomCodeIgnoreCase(String roomCode);

    List<Room> findAllByOrderByRoomCodeAsc();
}