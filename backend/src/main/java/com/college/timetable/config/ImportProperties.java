package com.college.timetable.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "college.import")
public class ImportProperties {

    /** Directory where validated uploads are stored. Never derived from an uploaded filename. */
    private String storageDir = "./data/imports";

    private String maxFileSize = "25MB";

    /** Hard cap on pages, protecting against decompression and processing abuse. */
    private int maxPages = 100;

    /** A page with fewer extracted characters than this is treated as needing OCR. */
    private int minCharsPerPage = 40;

    private Ocr ocr = new Ocr();

    /** Bulk import of a whole document, one section per page. */
    private Bootstrap bootstrap = new Bootstrap();

    /**
     * Settings for importing a published "section wise timetable" document in one pass, rather than
     * page by page through the review screen. Off by default.
     */
    public static class Bootstrap {
        private boolean enabled = false;
        private String source = "./data/imports/Section wise Timetable V15.pdf";
        private String academicYear = "2026-27";
        /**
         * The document prints a "week commencing" date but not term boundaries. These defaults are
         * derived from it and must be replaced with the college's official term dates.
         */
        private java.time.LocalDate effectiveFrom = java.time.LocalDate.of(2026, 7, 20);
        private java.time.LocalDate effectiveTo = java.time.LocalDate.of(2027, 6, 30);
        private boolean publish = true;
        /** Re-import a section that already has a published timetable. */
        private boolean republish = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getAcademicYear() {
            return academicYear;
        }

        public void setAcademicYear(String academicYear) {
            this.academicYear = academicYear;
        }

        public java.time.LocalDate getEffectiveFrom() {
            return effectiveFrom;
        }

        public void setEffectiveFrom(java.time.LocalDate effectiveFrom) {
            this.effectiveFrom = effectiveFrom;
        }

        public java.time.LocalDate getEffectiveTo() {
            return effectiveTo;
        }

        public void setEffectiveTo(java.time.LocalDate effectiveTo) {
            this.effectiveTo = effectiveTo;
        }

        public boolean isPublish() {
            return publish;
        }

        public void setPublish(boolean publish) {
            this.publish = publish;
        }

        public boolean isRepublish() {
            return republish;
        }

        public void setRepublish(boolean republish) {
            this.republish = republish;
        }
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public static class Ocr {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8081";
        private int timeoutSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getMinCharsPerPage() {
        return minCharsPerPage;
    }

    public void setMinCharsPerPage(int minCharsPerPage) {
        this.minCharsPerPage = minCharsPerPage;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public void setOcr(Ocr ocr) {
        this.ocr = ocr;
    }

    public Duration ocrTimeout() {
        return Duration.ofSeconds(ocr.getTimeoutSeconds());
    }
}