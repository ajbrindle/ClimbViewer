package com.sk7software.climbviewer.network;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileList implements Parcelable {
    private List<FileDescription> files;

    protected FileList(Parcel in) {
        files = in.readArrayList(FileDescription.class.getClassLoader());
    }

    public FileList(List<FileDescription> files) {
        this.files = files;
    }

    public static final Creator<FileList> CREATOR = new Creator<FileList>() {
        @Override
        public FileList createFromParcel(Parcel in) {
            return new FileList(in);
        }

        @Override
        public FileList[] newArray(int size) {
            return new FileList[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeList(files);
    }
}
