package com.jabstone.jabtalk.basic.storage;

import com.jabstone.jabtalk.basic.JTApp;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Created by joebu on 10/8/16.
 */

public class DataStoreFileFilter implements FilenameFilter {

    @Override
    public boolean accept(File file, String s) {
        return s.equals(DataStore.FILE_JSON_DATASET) || s.contains("images") || s.contains("audio")
                || file.equals(JTApp.getDataStore().getImageDirectory()) || file.equals(JTApp.getDataStore().getAudioDirectory());
    }
}
