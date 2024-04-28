package com.sk7software.climbviewer.model.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaItem {
    private String name;
    @JsonProperty("start_date_local")
    private String dateTime;
    private long id;
    private float distance;
    @JsonProperty("elevation_gain")
    private float elevGain;
    @JsonProperty("sport_type")
    private String sportType;
    private Object type;

    public String reformatDateTime() {
        if (dateTime == null) {
            return "";
        }
        LocalDateTime ldt = LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
        return ldt.format(DateTimeFormatter.ofPattern("EEE dd LLL yyyy HH:mm"));
    }

    public String formatDistance() {
        if (distance == 0) {
            return "";
        }
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(1);
        return df.format(distance/1000.0) + "km";
    }

    public String formatElevation() {
        if (elevGain == 0) {
            return "";
        }
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(1);
        return df.format(elevGain) + "m";
    }
}
