package com.sk7software.climbviewer.model;


import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import lombok.Getter;
import lombok.Setter;

@Root(strict=false)
@Getter
@Setter
public class Track {
    @Element
    private String name;

    @Element(name="trkseg")
    private TrackSegment trackSegment;
}
