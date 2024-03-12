package com.sk7software.climbviewer.model.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.common.util.Strings;
import com.sk7software.climbviewer.db.Preferences;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaAuth {
    private static final String EXPIRY_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private int expiresIn;

    public static StravaAuth fromPrefs() {
        StravaAuth auth = new StravaAuth();
        auth.setAccessToken(Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_STRAVA_AUTH_TOKEN));
        auth.setRefreshToken(Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_STRAVA_REFRESH_TOKEN));
        return auth;
    }

    public String calcExpiryTime() {
        LocalDateTime dateTime = LocalDateTime.now();
        dateTime.plusSeconds(expiresIn);
        return dateTime.format(DateTimeFormatter.ofPattern(EXPIRY_TIME_FORMAT));
    }

    public boolean hasExpired() {
        String expiryTime = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_STRAVA_EXPIRY);
        if (Strings.isEmptyOrWhitespace(expiryTime)) {
            return true;
        }

        LocalDateTime expiryDateTime = LocalDateTime.parse(expiryTime, DateTimeFormatter.ofPattern(EXPIRY_TIME_FORMAT));
        return expiryDateTime.isBefore(LocalDateTime.now());
    }

}
