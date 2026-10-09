package com.college.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_rooms_code", columnNames = {"room_code"}))
public class Room extends BaseEntity {

    /** e.g. AU6-4304 */
    @Column(name = "room_code", nullable = false, length = 40)
    private String roomCode;

    @Column(name = "building", length = 100)
    private String building;

    @Column(name = "floor", length = 20)
    private String floor;

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }
}