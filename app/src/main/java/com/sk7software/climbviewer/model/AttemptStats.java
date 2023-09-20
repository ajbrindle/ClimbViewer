package com.sk7software.climbviewer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttemptStats {
    String name;
    int id;
    float distanceM;
    int duration;
    int pb;
    int pos;
    int total;
    boolean thisAttemptIsPb;
}
