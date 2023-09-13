package com.sk7software.climbviewer.network;

import android.os.Parcel;
import android.os.Parcelable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileDescription implements Parcelable {
    private String name;
    private String description;
    private String time;

    public FileDescription() {}

    protected FileDescription(Parcel in) {
        name = in.readString();
        description = in.readString();
        time = in.readString();
    }

    public static final Creator<FileDescription> CREATOR = new Creator<FileDescription>() {
        @Override
        public FileDescription createFromParcel(Parcel in) {
            return new FileDescription(in);
        }

        @Override
        public FileDescription[] newArray(int size) {
            return new FileDescription[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(name);
        parcel.writeString(description);
        parcel.writeString(time);
    }
}
