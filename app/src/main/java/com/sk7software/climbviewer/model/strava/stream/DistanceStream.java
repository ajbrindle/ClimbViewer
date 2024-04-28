package com.sk7software.climbviewer.model.strava.stream;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DistanceStream implements Serializable, Parcelable {
    private List<Float> data = new ArrayList<>();
    @JsonProperty("original_size")
    private int size;

    protected DistanceStream(Parcel in) {
        size = in.readInt();
    }

    public static final Creator<DistanceStream> CREATOR = new Creator<DistanceStream>() {
        @Override
        public DistanceStream createFromParcel(Parcel in) {
            return new DistanceStream(in);
        }

        @Override
        public DistanceStream[] newArray(int size) {
            return new DistanceStream[size];
        }
    };

    public void addItem(float dist) {
        data.add(dist);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeInt(size);
    }
}
