package com.sk7software.climbviewer.model;

import java.util.List;

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

    public void calcStats(List<AttemptStats> attempts) {
        this.setTotal(attempts.size());

        int pbDuration = Integer.MAX_VALUE;
        int pos = 1;
        boolean first = true;

        for (AttemptStats attempt : attempts) {
            int duration = attempt.getDuration();

            if (first) {
                this.setId(attempt.getId());
                this.setDuration(duration);
                this.setName(attempt.getName());
                this.setThisAttemptIsPb(true);
            } else if (duration < this.getDuration()) {
                // Move attempt down a position as another one was quicker
                pos++;
            } else if (pos == 1 && duration == this.getDuration()) {
                // Another attempt equalled this one, so it isn't a new PB
                this.setThisAttemptIsPb(false);
            }

            // PB is from a previous attempt, not this one
            if (!first && duration < pbDuration) {
                this.setPb(duration);
                pbDuration = duration;
            }
            first = false;
        }

        this.setPos(pos);

        if (pos > 1 || total == 1) {
            this.setThisAttemptIsPb(false);
        }
    }
}
