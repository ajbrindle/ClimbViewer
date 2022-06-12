package com.sk7software.climbviewer.model;

import android.util.Log;

import com.google.gson.Gson;
import com.sk7software.climbviewer.db.Database;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Root(name="gpx", strict=false)
@Getter
@Setter
public class GPXFile {
    // EMULATOR
    private static final String FILE_DIR = "/data/data/com.sk7software.climbviewer/";
    // P20 Phone
    //public static final String FILE_DIR = "/sdcard/Android/data/com.sk7software.climbviewer/";

    private static final String GPX_EXT = ".gpx";
    private static final String TAG = GPXFile.class.getSimpleName();

    @Element
    private GPXMetadata metadata;

    @Element(name="rte")
    private GPXRoute route;

    public static void addLocalFiles() {
        // Get all files in MAP_DIR with .mhr extension
        File directory = new File(FILE_DIR);
        Serializer serializer = new Persister();

        List<File> files = Arrays.asList(directory.listFiles()).stream()
                .filter(file -> file.getName().endsWith(GPX_EXT)).collect(Collectors.toList());

        for (File f : files) {
            // Load the file
            Log.d(TAG, "Found local climb: " + f.getAbsolutePath());

            try {
                GPXFile gpx = serializer.read(GPXFile.class, f);
                if (gpx != null) {
                    Database.getInstance().addClimb(gpx);
                }
            } catch (Exception e) {
                Log.d(TAG, "Unable to read climb GPX: " + e.getMessage());
            }
        }
    }
}
