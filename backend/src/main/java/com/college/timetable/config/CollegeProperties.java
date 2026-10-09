package com.college.timetable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * College-wide configuration. All values are overridable through environment variables,
 * e.g. {@code COLLEGE_TIMEZONE=Asia/Kolkata}.
 */
@ConfigurationProperties(prefix = "college")
public class CollegeProperties {

    /** IANA timezone id used for every date/time calculation in the system. */
    private String timezone = "Asia/Kolkata";

    /** Display name of the college, shown in the UI and in generated timetable headers. */
    private String name = "College Timetable Lookup System";

    /**
     * When true, demo data (clearly labelled as synthetic test data) is inserted on startup
     * if the database has no students. Never enable this against real college data.
     */
    private boolean seedDemoData = false;

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSeedDemoData() {
        return seedDemoData;
    }

    public void setSeedDemoData(boolean seedDemoData) {
        this.seedDemoData = seedDemoData;
    }
}