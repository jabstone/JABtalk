package com.jabstone.jabtalk.basic.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;

public class Ideogram implements Serializable {	
		
	private static final long serialVersionUID = 14252433746L;
	private static String TAG = Ideogram.class.getSimpleName();
	private LinkedList<Ideogram> m_children = new LinkedList<>();
	
    private String id;
	private String label;
	private String phrase;
	private String audioExtension;
	private String imageExtension;
	private String tempAudioPath;
	private String tempImagePath;
	private boolean hidden;
	private Type type;
	private String parentId = null;
	
	public Ideogram(Type type) {
		this.setType(type);	
	}
	
	public Ideogram(Ideogram gram) {
		this.setId(gram.getId());
		this.setLabel(gram.getLabel());
		this.setParentId(gram.getParentId());
		this.setPhrase(gram.getPhrase());
		this.setAudioExtension(gram.getAudioExtension());
		this.setImageExtension(gram.getImageExtension());
		this.setHidden(gram.isHidden());	
		this.setType(gram.getType());
	}
	
	public LinkedList<Ideogram> getChildren(boolean includeHiddenItems) {
		if(includeHiddenItems) {
			return m_children;
		} else {
			LinkedList<Ideogram> filteredChildren = new LinkedList<>();
			for(Ideogram child : m_children) {
    			if(!child.isHidden()) {
    				filteredChildren.add(child);
    			}
    		}
    		return filteredChildren;
		}
	}	   

    public String getId() {
		return id;
	}

	public void setId(String guid) {
    	this.id = guid.trim();
    }

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label.trim();
	}

	public String getPhrase() {
		return phrase;
	}

	public void setPhrase(String phrase) {
		this.phrase = phrase != null ? phrase.trim() : "";
	}

	public Type getType() {
		return type;
	}
	
	public void setType(Type type) {
		this.type = type;
	}

	public String getAudioPath() {
		return getAudioPath(JTApp.getDataStore().getDataDirectory());
	}

	public String getAudioPath(File baseDirectory) {
		if(tempAudioPath != null) {
			return tempAudioPath;
		}

		if(audioExtension != null && audioExtension.trim().length() > 0) {
			return new File(baseDirectory, "audio").getAbsolutePath() + File.separator + getId() + "." + getAudioExtension();
		} else {
			return null;
		}
	}
	
	public void setTempAudioPath(String tempPath) {
		this.tempAudioPath = tempPath;
	}

	public String getImagePath() {
		return getImagePath(JTApp.getDataStore().getDataDirectory());
	}

	public String getImagePath(File baseDirectory) {
		if(tempImagePath != null) {
			return tempImagePath;
		}

		if(imageExtension != null && imageExtension.trim().length() > 0) {
			return new File(baseDirectory, "images").getAbsolutePath() + File.separator + getId() + "." + getImageExtension();
		} else {
			return null;
		}
	}
	
	public void setTempImagePath(String tempPath) {
		this.tempImagePath = tempPath;
	}
	
	public Bitmap getImage() {
		Bitmap bmp = null;
		if(getImagePath() != null) {
			File f = new File(getImagePath());
			if(f.exists() && f.length() > 0) {
				bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
			}
		}
		if(isTextButton()) {
			bmp = BitmapFactory.decodeResource(JTApp.getInstance().getResources(), R.drawable.chalkboard);
		}
		if(bmp == null) {
			bmp = BitmapFactory.decodeResource(JTApp.getInstance().getResources(), R.drawable.no_picture);
		}
		return bmp;
	}
	
	public String getParentId() {
		return parentId;
	}

	public void setParentId(String id) {
		if(id != null) {
			if(id.trim().length() < 1) {
				this.parentId = null;
			} else {
				this.parentId = id.trim();
			}
		}
	}

	public boolean isRoot() {
		return getParentId() == null;
	}
	
	public String getAudioExtension() {
		return audioExtension;
	}

	public void setAudioExtension(String audioExtension) {
		if(audioExtension != null) {
			int pos = audioExtension.lastIndexOf(".");
			if(pos > -1) {
				this.audioExtension = audioExtension.substring(pos + 1);
			} else {
				this.audioExtension = audioExtension;
			}
		} else {
			this.audioExtension = null;
		}
	}

	public String getImageExtension() {
		return imageExtension;
	}

	public void setImageExtension(String imageExtension) {
		if(imageExtension != null) {
			int pos = imageExtension.lastIndexOf(".");
			if(pos > -1) {
				this.imageExtension = imageExtension.substring(pos + 1);
			} else {
				this.imageExtension = imageExtension;
			}
		} else {
			this.imageExtension = null;
		}
	}

	public boolean isTextButton() {
		return imageExtension != null && imageExtension.equalsIgnoreCase(JTApp.EXTENSION_TEXT_IMAGE);
	}
	
	public boolean isSynthesizeButton() {
		return audioExtension != null && audioExtension.equalsIgnoreCase(JTApp.EXTENSION_SYNTHESIZER);
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	@Override
	public String toString() {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(DataStore.JSON_ID, getId());
			jsonObject.put(DataStore.JSON_AUDIO_EXT, getAudioExtension());
			jsonObject.put(DataStore.JSON_IMAGE_EXT, getImageExtension());
			jsonObject.put(DataStore.JSON_LABEL, getLabel());
			jsonObject.put(DataStore.JSON_PHRASE, getPhrase());
			jsonObject.put(DataStore.JSON_PARENT_ID, getParentId() == null ? "" : getParentId());
			jsonObject.put(DataStore.JSON_TYPE, getType() == Type.Category ? DataStore.JSON_TYPE_CATEGORY : DataStore.JSON_TYPE_WORD);
			jsonObject.put(DataStore.JSON_HIDDEN, isHidden());
			JSONArray jsonChildren = new JSONArray();
			for(Ideogram child : m_children) {
				JSONObject jsonChild = new JSONObject(child.toString());
				jsonChildren.put(jsonChild);
			}
			jsonObject.put(DataStore.JSON_CHILDREN, jsonChildren);
		}
		catch(JSONException jse) {
			JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Failed to produce category JSON string");
		}
		return jsonObject.toString();
	}

	public enum Type {
		Category("c"), Word("w");

		private String value;

		Type(String value) {
			this.value = value;
		}

		public static Type getType(String value) {
			Type t = Category;
			if (value.equalsIgnoreCase("w")) {
				t = Word;
			}
			return t;
		}

		public String getValue() {
			return this.value;
		}

	}
	
}
