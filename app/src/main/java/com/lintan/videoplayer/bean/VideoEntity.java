package com.lintan.videoplayer.bean;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * the entity to display.
 * <p/>
 * Created by lintan on 9/2/16.
 */
public class VideoEntity implements Parcelable {

	private long idDb;
	private String thumbnailPath;

	private String name;
	private String mimeType;

	// video path
	private String data;

	private long size;
	private long during;
	private long timeToken;
	private long lastModify;

	// it's not ok
	private int orientation;

	private int width;
	private int height;

	// modify because an object translate in a app is useful.
	private long currentPosition;
	// to save current play state.
	private boolean isPlaying;

	public long getIdDb() {
		return idDb;
	}

	public void setIdDb(long idDb) {
		this.idDb = idDb;
	}

	public String getThumbnailPath() {
		return thumbnailPath;
	}

	public void setThumbnailPath(String thumbnailPath) {
		this.thumbnailPath = thumbnailPath;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public long getDuring() {
		return during;
	}

	public void setDuring(long during) {
		this.during = during;
	}

	public long getTimeToken() {
		return timeToken;
	}

	public void setTimeToken(long timeToken) {
		this.timeToken = timeToken;
	}

	public long getLastModify() {
		return lastModify;
	}

	public void setLastModify(long lastModify) {
		this.lastModify = lastModify;
	}

	public int getOrientation() {
		return orientation;
	}

	public void setOrientation(int orientation) {
		this.orientation = orientation;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public long getCurrentPosition() {
		return currentPosition;
	}

	public void setCurrentPosition(long currentPosition) {
		this.currentPosition = currentPosition;
	}

	public boolean isPlaying() {
		return isPlaying;
	}

	public void setPlaying(boolean playing) {
		isPlaying = playing;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("currentPosition: ").append(currentPosition);
		sb.append("orientation: ").append(orientation);
		sb.append("name: ").append(name);
		sb.append("width: ").append(width);
		sb.append("height: ").append(height);
		return sb.toString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(idDb);
		dest.writeString(thumbnailPath);

		dest.writeString(name);
		dest.writeString(mimeType);
		dest.writeString(data);

		dest.writeLong(size);
		dest.writeLong(during);
		dest.writeLong(timeToken);
		dest.writeLong(lastModify);
		dest.writeInt(orientation);
		dest.writeInt(width);
		dest.writeInt(height);
		dest.writeLong(currentPosition);
		dest.writeByte((byte) (isPlaying ? 1 : 0));
	}

	public static final Parcelable.Creator<VideoEntity> CREATOR = new Parcelable.Creator<VideoEntity>() {
		@Override
		public VideoEntity createFromParcel(Parcel source) {
			VideoEntity entity = new VideoEntity();
			entity.idDb = source.readLong();
			entity.thumbnailPath = source.readString();

			entity.name = source.readString();
			entity.mimeType = source.readString();
			entity.data = source.readString();

			entity.size = source.readLong();
			entity.during = source.readLong();
			entity.timeToken = source.readLong();
			entity.lastModify = source.readLong();
			entity.orientation = source.readInt();
			entity.width = source.readInt();
			entity.height = source.readInt();
			entity.currentPosition = source.readLong();
			entity.isPlaying = source.readByte() != 0;
			return entity;
		}

		@Override
		public VideoEntity[] newArray(int size) {
			return new VideoEntity[size];
		}
	};
}
