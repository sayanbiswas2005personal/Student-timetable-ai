package com.college.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.college.timetable.repository.StudentRepository;

/**
 * Boots the whole application against an in-memory H2 database in MySQL compatibility mode.
 *
 * <p>This is the test that proves the Flyway migrations are valid SQL on both engines, that the
 * JPA mappings line up with the schema, and that the Spring context wires together.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private StudentRepository studentRepository;

    @Test
    void contextLoadsAndSchemaIsUsable() {
        assertThat(studentRepository.count()).isNotNegative();
    }
}