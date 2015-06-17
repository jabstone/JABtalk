package com.jabstone.jabtalk.basic.storage;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.JTApp;

public class Ideogram implements Serializable {	
		
	private static String TAG = Ideogram.class.getSimpleName();
	private static final long serialVersionUID = 14252433746L;
	private LinkedList<Ideogram> m_children = new LinkedList<Ideogram>();
	
    private String id;
	private String label;
	private String phrase;
	private String audioExtention;
	private String imageExtention;
	private String tempAudioPath;
	private String tempImagePath;
	private boolean hidden;
	private Type type;	
	
	public enum Type {
        Category("c"), Word("w");
        
        private String value;
        
        private Type(String value) {
        	this.value = value;
        }
        
        public String getValue() {
        	return this.value;
        }
        
        public static Type getType(String value) {
        	Type t = Category;
        	if(value.equalsIgnoreCase("w")) {
        		t = Word;
        	}
        	return t;
        }
        
    }
	
	public Ideogram(Type type) {
		this.setType(type);	
	}
	
	public Ideogram(Ideogram gram) {
		this.setId(gram.getId());
		this.setLabel(gram.getLabel());
		this.setParentId(gram.getParentId());
		this.setPhrase(gram.getPhrase());
		this.setAudioExtention(gram.getAudioExtention());
		this.setImageExtention(gram.getImageExtention());
		this.setHidden(gram.isHidden());	
		this.setType(gram.getType());
	}
	
	public LinkedList<Ideogram> getChildren(boolean includeHiddenItems) {
		if(includeHiddenItems) {
			return m_children;
		} else {
    		LinkedList<Ideogram> filteredChildren = new LinkedList<Ideogram> ();
    		for(Ideogram child : m_children) {
    			if(!child.isHidden()) {
    				filteredChildren.add(child);
    			}
    		}
    		return filteredChildren;
		}
	}	   

	private String parentId = null;
	
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
		if(tempAudioPath != null) {
			return tempAudioPath;
		}
		
		if(audioExtention != null && audioExtention.trim().length() > 0) {
			return JTApp.getDataStore().getAudioDirectory().getAbsolutePath() + File.separator + getId() + "." + getAudioExtention();
		} else {
			return null;
		}
	}
	
	public void setTempAudioPath(String tempPath) {
		this.tempAudioPath = tempPath;
	}
	
	public String getImagePath() {
		if(tempImagePath != null) {
			return tempImagePath;
		}
		
		if(imageExtention != null && imageExtention.trim().length() > 0) {
			return JTApp.getDataStore().getImageDirectory().getAbsolutePath() + File.separator + getId() + "." + getImageExtention();
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
	
	public String getAudioExtention() {
		return audioExtention;
	}

	public void setAudioExtention(String audioExtention) {
		if(audioExtention != null) {
			int pos = audioExtention.lastIndexOf(".");
			if(pos > -1) {
				this.audioExtention = audioExtention.substring(pos + 1);
			} else {
				this.audioExtention = audioExtention;
			}
		} else {
			this.audioExtention = null;
		}
	}

	public String getImageExtention() {
		return imageExtention;
	}

	public void setImageExtention(String imageExtention) {
		if(imageExtention != null) {
			int pos = imageExtention.lastIndexOf(".");
			if(pos > -1) {
				this.imageExtention = imageExtention.substring(pos + 1);
			} else {
				this.imageExtention = imageExtention;
			}
		} else {
			this.imageExtention = null;
		}
	}
	
	public boolean isTextButton() {
		if(imageExtention != null && imageExtention.equalsIgnoreCase(JTApp.EXTENSION_TEXT_IMAGE)) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean isSynthesizeButton() {
		if(audioExtention != null && audioExtention.equalsIgnoreCase(JTApp.EXTENSION_SYNTHESIZER)) {
			return true;
		} else {
			return false;
		}
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
			jsonObject.put(DataStore.JSON_AUDIO_EXT, getAudioExtention());
			jsonObject.put(DataStore.JSON_IMAGE_EXT, getImageExtention());			
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
	
}
