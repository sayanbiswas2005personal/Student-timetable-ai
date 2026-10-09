package com.college.timetable.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "college.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Login attempts allowed per IP per window. */
    private int loginAttempts = 10;

    private Duration loginWindow = Duration.ofMinutes(5);

    /** Student lookups allowed per user per window. */
    private int lookupRequests = 240;

    private Duration lookupWindow = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(int loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    public Duration getLoginWindow() {
        return loginWindow;
    }

    public void setLoginWindow(Duration loginWindow) {
        this.loginWindow = loginWindow;
    }

    public int getLookupRequests() {
        return lookupRequests;
    }

    public void setLookupRequests(int lookupRequests) {
        this.lookupRequests = lookupRequests;
    }

    public Duration getLookupWindow() {
        return lookupWindow;
    }

    public void setLookupWindow(Duration lookupWindow) {
        this.lookupWindow = lookupWindow;
    }
}