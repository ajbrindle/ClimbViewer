package com.sk7software.climbviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.sk7software.climbviewer.model.GPXFile;

import org.junit.Test;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.StringReader;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class UnmarshalGPXTest {
    @Test
    public void validGPXData() throws Exception {
        String xmlStr = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<gpx xmlns:gh=\"https://graphhopper.com/public/schema/gpx/1.1\" xmlns:gs=\"http://www.topografix.com/GPX/gpx_style/0/2\" xmlns:os=\"https://ordnancesurvey.co.uk/public/schema/route/0.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"OSMaps\" version=\"1.1\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1/gpx.xsd\">\n" +
                "<metadata>\n" +
                "\t<name>Brickworks Climb</name>\n" +
                "\t<bounds maxlat=\"53.3209845\" maxlon=\"-2.0396452\" minlat=\"53.3079987\" minlon=\"-2.1348941\"/>\n" +
                "</metadata>\n" +
                "<rte>\n" +
                "\t<name>Brickworks</name>\n" +
                "\t<rtept lat=\"53.3207797\" lon=\"-2.1349345\">\n" +
                "\t\t<ele>103.1</ele>\n" +
                "\t</rtept>\n" +
                "\t<rtept lat=\"53.3209189\" lon=\"-2.1310318\">\n" +
                "\t\t<ele>99.8</ele>\n" +
                "\t</rtept>\n" +
                "</rte>\n" +
                "</gpx>";

        Serializer serializer = new Persister();
        GPXFile gpx = serializer.read(GPXFile.class, xmlStr);

        assertEquals(2, gpx.getRoute().getPoints().size());
        assertEquals("Brickworks Climb", gpx.getMetadata().getName());
        assertEquals("Brickworks", gpx.getRoute().getName());
        assertEquals(53.3207797, gpx.getRoute().getPoints().get(0).getLat(), 0.0001);
        assertEquals(103.1, gpx.getRoute().getPoints().get(0).getElevation(), 0.0001);
        assertEquals(-2.1310318, gpx.getRoute().getPoints().get(1).getLon(), 0.0001);
        assertEquals(99.8, gpx.getRoute().getPoints().get(1).getElevation(), 0.0001);
    }
}