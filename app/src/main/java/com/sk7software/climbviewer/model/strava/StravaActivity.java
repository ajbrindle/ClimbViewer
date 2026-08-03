package com.sk7software.climbviewer.model.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivity {
    private String name;
    private String description;
    @JsonProperty("start_date")
    private String startTime;
    @JsonProperty("elapsed_time")
    private Long duration;
}
