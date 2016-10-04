package com.jabstone.jabtalk.basic.storage;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.os.Environment;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;


public class DataStore {

    private static String TAG = DataStore.class.getSimpleName ();
    private final String FILE_JSON = "jabtalk.json";
    private final String FILE_BACKUP = "jabtalk.bak";

    private final String VERSION = "versionId";
    private final String CATEGORYLIST = "categoryList";

    private final String JSON_IDEOGRAMS = "rig";
    public static final String JSON_ID = "id";
    public static final String JSON_PARENT_ID = "pid";
    public static final String JSON_IMAGE_EXT = "ie";
    public static final String JSON_AUDIO_EXT = "ae";
    public static final String JSON_LABEL = "l";
    public static final String JSON_PHRASE = "p";
    public static final String JSON_TYPE = "t";
    public static final String JSON_CHILDREN = "ch";
    public static final String JSON_TYPE_CATEGORY = "c";
    public static final String JSON_TYPE_WORD = "w";
    public static final String JSON_TYPE_ACTION = "a";
    public static final String JSON_HIDDEN = "h";
    // Legacy Tags
    public static final String JSON_CATEGORY_LIST = "cl";
    public static final String JSON_IDEOGRAM_LIST = "il";

    private File m_store = null;
    private Map<String, Ideogram> m_ideogramMap = new HashMap<String, Ideogram> ();
    private Ideogram m_rootCategory = null;
    private String versionId = null;

    public DataStore () {
        this.refreshStore ();
        if(getDataDirectory().equals(JTApp.getInstance().getFilesDir())) {
        	JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, "Using internal storage");
        } else {
        	JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO, "Using external storage");
        }
    }

    public void refreshStore () {
        getRootCategory ();
        m_store = new File ( getDataDirectory (), FILE_JSON );
        try {
            loadData ();
        } catch ( Exception e ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to load DataStore. Error Details: " + getStackTrace ( e ) );
        }
    }

    public Ideogram getRootCategory () {
        if ( m_rootCategory == null ) {
            m_rootCategory = new Ideogram ( Type.Category );
            m_rootCategory.setId ( UUID.randomUUID ().toString () );
            m_ideogramMap.clear ();
            m_ideogramMap.put ( m_rootCategory.getId (), m_rootCategory );
        }
        return m_rootCategory;
    }

    public Map<String, Ideogram> getIdeogramMap () {
        return m_ideogramMap;
    }

    public Ideogram getIdeogram ( String id ) {
        Ideogram gram = m_ideogramMap.get ( id );
        return gram;
    }

    // Remove ideogram from datastructure but don't commit changes until
    // commitDelete is called
    public void deleteIdeogram ( String id ) {

        Ideogram gram = m_ideogramMap.get ( id );
        if ( gram != null ) {
            List<Ideogram> deleteList = new LinkedList<Ideogram> ();
            Ideogram parent = m_ideogramMap.get ( gram.getParentId () );
            deleteList.addAll ( getAllIdeogramsForCategory ( gram ) );
            parent.getChildren ( true ).remove ( gram );

            // Delete all files for ideogram and ideogram's children
            for ( Ideogram g : deleteList ) {
                m_ideogramMap.remove ( g.getId () );
            }
        }
    }

    public void clearCache () {
        try {
            File path = getTempDirectory ();
            if ( path != null && path.exists () ) {
                File[] files = path.listFiles ();
                for ( File f : files ) {
                    deleteDir ( f );
                }
            }
        } catch ( Exception e ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to delete cache directory.  Error Details: " + getStackTrace ( e ) );
        }
    }

    public File getTempDirectory () {
        File f = new File ( JTApp.getInstance().getExternalCacheDir(), "jabcache" );
        if ( !f.exists () ) {
            f.mkdirs ();
        }

        return f;
    }

    public File getImageDirectory () {
        File imageDir = new File ( getDataDirectory (), "images" );
        if ( !imageDir.exists () ) {
            imageDir.mkdirs ();
        }
        return imageDir;

    }

    public File getAudioDirectory () {
        File audioDir = new File ( getDataDirectory (), "audio" );
        if ( !audioDir.exists () ) {
            audioDir.mkdirs ();
        }
        return audioDir;
    }

    public File getDataDirectory () {
    	File f = JTApp.getInstance ().getExternalFilesDir(null);
    	File existingInstall = new File(f, FILE_JSON);
    	if(!existingInstall.exists()) {    		
    		f = JTApp.getInstance ().getFilesDir(); 
    	}
    	
        return f;
    }

    public File getExternalStorageDirectory () {
        File dir = Environment.getExternalStorageDirectory ();
        return dir;
    }

    public void copyIdeogram ( String sourceId, String targetId ) throws JabException {

        Ideogram source = m_ideogramMap.get ( sourceId );
        if ( source == null ) {
            throw new JabException ( JTApp.getInstance ().getApplicationContext ()
                    .getString ( com.jabstone.jabtalk.basic.R.string.copy_error_source_not_found ) );
        }

        Ideogram target = null;
        if ( targetId != null ) {
            target = m_ideogramMap.get ( targetId );
        }
        cloneIdeogram ( sourceId, target );
    }

    public void cutIdeogram ( String sourceId, String targetId ) throws JabException {

        Ideogram source = m_ideogramMap.get ( sourceId );
        if ( source == null ) {
            throw new JabException ( JTApp.getInstance ().getApplicationContext ()
                    .getString ( com.jabstone.jabtalk.basic.R.string.copy_error_source_not_found ) );
        }

        Ideogram sourceParent = m_ideogramMap.get ( source.getParentId () );
        Ideogram target = null;
        if ( targetId != null ) {
            target = m_ideogramMap.get ( targetId );
        }

        // Prevent pasting to invalid sources
        if ( !isSafeToMove ( source, target ) ) {
            throw new JabException ( JTApp.getInstance ().getApplicationContext ()
                    .getString ( com.jabstone.jabtalk.basic.R.string.cut_error_invalid_target ) );
        }

        // move ideogram to new target and remove from old parent
        switch ( source.getType () ) {
            case Category:
                source.setType ( Type.Category );
                // Move item to new category
                source.setParentId ( targetId );
                target.getChildren ( true ).add ( source );

                // Remove item from old parent
                if ( sourceParent != null ) {
                    sourceParent.getChildren ( true ).remove ( source );
                }
                break;
            case Word:
                if ( target == null ) {
                    throw new JabException (
                            JTApp.getInstance ()
                                    .getApplicationContext ()
                                    .getString (
                                            com.jabstone.jabtalk.basic.R.string.cut_error_invalid_target_for_source ) );
                }
                source.setParentId ( target.getId () );
                target.getChildren ( true ).add ( source );
                sourceParent.getChildren ( true ).remove ( source );
                break;
        }
    }

    public boolean isSafeToMove ( Ideogram source, Ideogram target ) {       
        if (
        		(target.getType() == Type.Word ) || //Don't allow pasting anything to a word
                (source.getId ().equals(target.getId())) ||  //Don't allow pasting to itself
                (source.getParentId().equals(target.getId())) || //Don't allow pasting to parent twice
                (JTApp.getDataStore().getAllChildCategories(source).contains(target))) { //Don't allow pasting parent to child 
            return false;
        } else {
            return true;
        }
    }

    private boolean deleteDir ( File dir ) {
        if ( dir.isDirectory () ) {
            String[] children = dir.list ();
            for ( int i = 0; i < children.length; i++ ) {
                deleteDir ( new File ( dir, children[i] ) );
            }
        }
        return dir.delete ();
    }

    private void cloneIdeogram ( String sourceId, Ideogram target ) throws JabException {
        Ideogram source = m_ideogramMap.get ( sourceId );
        String newId = UUID.randomUUID ().toString ();
        Ideogram clone = new Ideogram ( source );

        clone.setId ( newId );
        clone.setParentId ( target.getId () );

        if ( source.getImagePath () != null ) {
            File imgSource = new File ( source.getImagePath () );
            if ( imgSource.exists () ) {
                File imgDestination = new File ( getImageDirectory (), clone.getId () + "."
                        + source.getImageExtention () );
                copyFile ( imgSource, imgDestination );
            }
        }
        if ( source.getAudioPath () != null ) {
            File audSource = new File ( source.getAudioPath () );
            if ( audSource.exists () ) {
                File audDestination = new File ( getAudioDirectory (), clone.getId () + "."
                        + source.getAudioExtention () );
                copyFile ( audSource, audDestination );
            }
        }

        if ( target != null ) {
            target.getChildren ( true ).add ( clone );
        }

        for ( Ideogram child : source.getChildren ( true ) ) {
            cloneIdeogram ( child.getId (), clone );
        }

        m_ideogramMap.put ( clone.getId (), clone );
    }

    public boolean copyFile ( File source, File destination ) throws JabException {
        boolean result = false;
        if ( source.exists () ) {
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;
            try {
                bis = new BufferedInputStream ( new FileInputStream ( source ) );
                bos = new BufferedOutputStream ( new FileOutputStream ( destination ) );
                byte[] data = new byte[1024];
                int size = 0;
                while ( ( size = bis.read ( data, 0, 1024 ) ) > -1 ) {
                    bos.write ( data, 0, size );
                }
                bis.close ();
                bos.flush ();
                bos.close ();
                result = true;
            } catch ( IOException io ) {
                JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Failed to copy file.  Error Details: " + getStackTrace ( io ) );
                throw new JabException ( "Failed to import file \"" + source.getName ()
                        + "\". Click the menu and select Log to view error details." );
            } finally {
                try {
                    if ( bis != null ) {
                        bis.close ();
                    }
                    if ( bos != null ) {
                        bos.close ();
                    }
                } catch ( Exception ignore ) {
                }
            }
        }
        return result;
    }

    public boolean saveScaledImage ( File fileName, Bitmap bmp ) throws JabException {

        boolean result = false;
        try {
            if ( getFileExtention ( fileName.getName (), false ).equalsIgnoreCase ( "png" ) ) {
                result = bmp.compress ( Bitmap.CompressFormat.PNG, 85, new FileOutputStream (
                        fileName ) );
            } else {
                result = bmp.compress ( Bitmap.CompressFormat.JPEG, 85, new FileOutputStream (
                        fileName ) );
            }
        } catch ( Exception e ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Error scaling image. Error Details: " + getStackTrace ( e ) );
            throw new JabException (
                    "Error shrinking image for use in JABtalk. Click the menu and select Log to view error details." );
        }
        return result;
    }

    public void saveDataStore () throws JabException {
        removeOrphans ();
        clearCache ();

        JSONObject jsonObject = new JSONObject ();
        OutputStreamWriter writer = null;
        try {
            jsonObject.put ( VERSION, JTApp.DATASTORE_VERSION );
            JSONObject graph = new JSONObject ( m_rootCategory.toString () );
            jsonObject.put ( JSON_IDEOGRAMS, graph );
            writer = new OutputStreamWriter ( new FileOutputStream ( m_store ), "UTF-8" );
            writer.write ( jsonObject.toString () );
            writer.flush ();
            writer.close ();
        } catch ( JSONException jse ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to save DataStore.  Error Details: " + getStackTrace ( jse ) );
            throw new JabException (
                    "Failed to save changes. Click the menu and select Log to view error details." );
        } catch ( IOException io ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to save DataStore.  Error Details: " + getStackTrace ( io ) );
            if ( !Environment.MEDIA_MOUNTED.equals ( Environment.getExternalStorageState () ) ) {
                JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                        "It appears your SDCard is not currently accessible or is in read-only mode" );
            }
            throw new JabException (
                    "Failed to save changes. Click the menu and select Log to view error details." );
        } finally {
            try {
                if ( writer != null ) {
                    writer.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
    }

    public boolean backupExists () {
        boolean result = false;
        try {
            File backupFile = new File ( getExternalStorageDirectory (), FILE_BACKUP );
            return backupFile.exists ();
        } catch ( Exception e ) {
        }
        return result;
    }

    public void backupDataStore () throws JabException {
        ZipOutputStream zos = null;
        try {
            File zipFileName = new File ( getExternalStorageDirectory (), FILE_BACKUP );
            File dataDir = getDataDirectory ();
            zos = new ZipOutputStream ( new FileOutputStream ( zipFileName, false ) );
            zos.setLevel ( Deflater.DEFAULT_COMPRESSION );
            zipDirectory ( dataDir, zos );
            zos.flush ();
            zos.close ();
        } catch ( Exception e ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to backup DataStore.  Error Details: " + getStackTrace ( e ) );
            throw new JabException (
                    "There was a problem backing up your data. Click the menu and select Log to view error details." );
        } finally {
            try {
                if ( zos != null ) {
                    zos.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
    }

    public void restoreDataStore () throws JabException {

        byte[] buffer = new byte[1024];
        InputStream in = null;
        FileOutputStream out = null;

        try {
            deleteAllFiles ();
            File zipFileName = new File ( getExternalStorageDirectory (), FILE_BACKUP );
            if ( !zipFileName.exists () ) {
                throw new JabException (
                        "Could not find the backup file \"jabtalk.bak\" on the SDCard" );
            }
            ZipFile zipFile = new ZipFile ( zipFileName );

            Enumeration<? extends ZipEntry> entries;
            File dataDir = getDataDirectory ();
            entries = zipFile.entries ();

            while ( entries.hasMoreElements () ) {
                ZipEntry entry = ( ZipEntry ) entries.nextElement ();

                if ( entry.isDirectory () ) {
                    File newDir = new File ( dataDir, entry.getName () );
                    if ( !newDir.exists () ) {
                        newDir.mkdirs ();
                    }
                    continue;
                }

                int len;
                in = zipFile.getInputStream ( entry );
                File target = new File ( dataDir, entry.getName () );
                out = new FileOutputStream ( target );
                while ( ( len = in.read ( buffer ) ) >= 0 )
                    out.write ( buffer, 0, len );

                in.close ();
                out.flush ();
                out.close ();
            }

            zipFile.close ();
            loadData ();

        } catch ( Exception ioe ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to restore backup.  Error Details: " + getStackTrace ( ioe ) );
            throw new JabException (
                    "Failed to restore backup file. Click the menu and select Log to view error details." );
        } finally {
            try {
                if ( in != null ) {
                    in.close ();
                }
                if ( out != null ) {
                    out.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
    }


    public void backupDataStore (String fileName) throws JabException {
        ZipOutputStream zos = null;
        try {
            File zipFileName = new File ( getExternalStorageDirectory (), fileName );
            File dataDir = getDataDirectory ();
            zos = new ZipOutputStream ( new FileOutputStream ( zipFileName, false ) );
            zos.setLevel ( Deflater.DEFAULT_COMPRESSION );
            zipDirectory ( dataDir, zos );
            zos.flush ();
            zos.close ();
        } catch ( Exception e ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to backup DataStore.  Error Details: " + getStackTrace ( e ) );
            throw new JabException (
                    "There was a problem backing up your data. Click the menu and select Log to view error details." );
        } finally {
            try {
                if ( zos != null ) {
                    zos.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
    }

    public void restoreDataStore (String fileName) throws JabException {

        byte[] buffer = new byte[1024];
        InputStream in = null;
        FileOutputStream out = null;

        try {
            deleteAllFiles ();
            File zipFileName = new File ( getExternalStorageDirectory (), fileName );
            if ( !zipFileName.exists () ) {
                throw new JabException (
                        "Could not find the backup file \"" + fileName + "\" on the SDCard" );
            }
            ZipFile zipFile = new ZipFile ( zipFileName );

            Enumeration<? extends ZipEntry> entries;
            File dataDir = getDataDirectory ();
            entries = zipFile.entries ();

            while ( entries.hasMoreElements () ) {
                ZipEntry entry = ( ZipEntry ) entries.nextElement ();

                if ( entry.isDirectory () ) {
                    File newDir = new File ( dataDir, entry.getName () );
                    if ( !newDir.exists () ) {
                        newDir.mkdirs ();
                    }
                    continue;
                }

                int len;
                in = zipFile.getInputStream ( entry );
                File target = new File ( dataDir, entry.getName () );
                out = new FileOutputStream ( target );
                while ( ( len = in.read ( buffer ) ) >= 0 )
                    out.write ( buffer, 0, len );

                in.close ();
                out.flush ();
                out.close ();
            }

            zipFile.close ();
            loadData ();

        } catch ( Exception ioe ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Failed to restore backup.  Error Details: " + getStackTrace ( ioe ) );
            throw new JabException (
                    "Failed to restore backup file. Click the menu and select Log to view error details." );
        } finally {
            try {
                if ( in != null ) {
                    in.close ();
                }
                if ( out != null ) {
                    out.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
    }

    private void deleteAllFiles () throws Exception {
        try {
            clearCache ();
            File[] files = getDataDirectory ().listFiles ();
            for ( File f : files ) {
                if ( !f.isDirectory () ) {
                    f.delete ();
                }
            }
            files = getAudioDirectory ().listFiles ();
            for ( File f : files ) {
                if ( !f.isDirectory () ) {
                    f.delete ();
                }
            }
            files = getImageDirectory ().listFiles ();
            for ( File f : files ) {
                if ( !f.isDirectory () ) {
                    f.delete ();
                }
            }
        } catch ( NullPointerException np ) {
            if ( !Environment.MEDIA_MOUNTED.equals ( Environment.getExternalStorageState () ) ) {
                throw new Exception (
                        "Error clearing files. It appears your SDCard is not currently accessible or is in read-only mode" );
            }
        }
    }

    private void zipDirectory ( File directory, ZipOutputStream zos ) throws Exception {
        String[] dirList = directory.list ();
        byte[] readBuffer = new byte[1024];
        int bytesIn = 0;

        for ( int i = 0; i < dirList.length; i++ ) {
            File f = new File ( directory, dirList[i] );
            if ( f.isDirectory () ) {
                zipDirectory ( f, zos );
                continue;
            }

            FileInputStream fis = new FileInputStream ( f );
            ZipEntry anEntry = null;
            if ( f.getPath ().contains ( getImageDirectory ().getAbsolutePath () )
                    || f.getPath ().contains ( getAudioDirectory ().getAbsolutePath () ) ) {
                anEntry = new ZipEntry ( f.getParentFile ().getName () + File.separator
                        + f.getName () );
            } else {
                anEntry = new ZipEntry ( f.getName () );
            }
            zos.putNextEntry ( anEntry );
            while ( ( bytesIn = fis.read ( readBuffer ) ) != -1 ) {
                zos.write ( readBuffer, 0, bytesIn );
            }
            fis.close ();
        }
    }

    private void loadData () throws Exception {
        InputStreamReader reader = null;

        try {
            if ( m_store != null && m_store.exists () && m_store.isFile () && m_store.length () > 0 ) {
                m_ideogramMap.clear ();

                // Read file into character array
                char[] data = new char[( int ) m_store.length ()];

                reader = new InputStreamReader ( new FileInputStream ( m_store ), "UTF-8" );
                reader.read ( data, 0, data.length );

                // Turn JSON String into object graph
                JSONObject jsonObject = new JSONObject ( new String ( data ) );
                versionId = getJSONString ( jsonObject, VERSION );

                if ( !versionId.equals ( JTApp.DATASTORE_VERSION ) ) {
                    // Convert store to new format
                    m_rootCategory = null;
                    getRootCategory ();
                    LinkedList<Ideogram> categoryList = loadLegacyData ( jsonObject );
                    m_rootCategory.getChildren ( true ).addAll ( categoryList );
                } else {
                    JSONObject jsonRoot = jsonObject.getJSONObject ( JSON_IDEOGRAMS );
                    m_rootCategory = inflateJSONCategory ( jsonRoot );
                }
            }
        } catch ( Exception jse ) {
            throw jse;
        } finally {
            try {
                if ( reader != null ) {
                    reader.close ();
                }
            } catch ( Exception ignore ) {
            }
        }
    }

    private LinkedList<Ideogram> loadLegacyData ( JSONObject jsonObject ) throws JSONException {
        LinkedList<Ideogram> categoryList = new LinkedList<Ideogram> ();
        JSONArray jsonChildren = jsonObject.getJSONArray ( CATEGORYLIST );
        for ( int i = 0; i < jsonChildren.length (); i++ ) {
            JSONObject jsonChild = jsonChildren.getJSONObject ( i );
            Ideogram category = inflateLegacyJSONCategory ( jsonChild );
            category.setParentId ( getRootCategory ().getId () );
            categoryList.add ( category );
        }
        return categoryList;
    }

    private Ideogram inflateJSONCategory ( JSONObject jsonCategory ) throws JSONException {
        Ideogram category = new Ideogram ( Type.Category );
        category.setId ( getJSONString ( jsonCategory, DataStore.JSON_ID ) );
        category.setAudioExtention ( getJSONString ( jsonCategory, DataStore.JSON_AUDIO_EXT ) );
        category.setImageExtention ( getJSONString ( jsonCategory, DataStore.JSON_IMAGE_EXT ) );
        category.setLabel ( getJSONString ( jsonCategory, DataStore.JSON_LABEL ) );
        category.setPhrase ( getJSONString ( jsonCategory, DataStore.JSON_PHRASE ) );
        category.setParentId ( getJSONString ( jsonCategory, DataStore.JSON_PARENT_ID ) );
        category.setHidden ( getJSONBoolean ( jsonCategory, DataStore.JSON_HIDDEN ) );
        if ( jsonCategory.has ( DataStore.JSON_CHILDREN ) ) {
            JSONArray jsonChildren = jsonCategory.getJSONArray ( DataStore.JSON_CHILDREN );
            for ( int i = 0; i < jsonChildren.length (); i++ ) {
                JSONObject jsonChild = jsonChildren.getJSONObject ( i );
                String type = getJSONString ( jsonChild, DataStore.JSON_TYPE );
                if ( Type.getType ( type ) == Type.Category ) {
                    Ideogram subCategory = inflateJSONCategory ( jsonChild );
                    category.getChildren ( true ).add ( subCategory );
                } else {
                    Ideogram word = new Ideogram ( Type.Word );
                    word.setId ( getJSONString ( jsonChild, DataStore.JSON_ID ) );
                    word.setAudioExtention ( getJSONString ( jsonChild, DataStore.JSON_AUDIO_EXT ) );
                    word.setImageExtention ( getJSONString ( jsonChild, DataStore.JSON_IMAGE_EXT ) );
                    word.setLabel ( getJSONString ( jsonChild, DataStore.JSON_LABEL ) );
                    word.setPhrase ( getJSONString ( jsonChild, DataStore.JSON_PHRASE ) );
                    word.setParentId ( getJSONString ( jsonChild, DataStore.JSON_PARENT_ID ) );
                    word.setHidden ( getJSONBoolean ( jsonChild, DataStore.JSON_HIDDEN ) );
                    category.getChildren ( true ).add ( word );
                    m_ideogramMap.put ( word.getId (), word );
                }

            }
        }

        m_ideogramMap.put ( category.getId (), category );
        return category;
    }

    private Ideogram inflateLegacyJSONCategory ( JSONObject jsonCategory ) throws JSONException {
        Ideogram category = new Ideogram ( Type.Category );
        category.setId ( getJSONString ( jsonCategory, DataStore.JSON_ID ) );
        category.setAudioExtention ( getJSONString ( jsonCategory, DataStore.JSON_AUDIO_EXT ) );
        category.setImageExtention ( getJSONString ( jsonCategory, DataStore.JSON_IMAGE_EXT ) );
        category.setLabel ( getJSONString ( jsonCategory, DataStore.JSON_LABEL ) );
        category.setPhrase ( getJSONString ( jsonCategory, DataStore.JSON_PHRASE ) );
        category.setParentId ( getJSONString ( jsonCategory, DataStore.JSON_PARENT_ID ) );
        category.setHidden ( getJSONBoolean ( jsonCategory, DataStore.JSON_HIDDEN ) );
        if ( jsonCategory.has ( DataStore.JSON_CATEGORY_LIST ) ) {
            JSONArray jsonCategoryList = jsonCategory.getJSONArray ( DataStore.JSON_CATEGORY_LIST );
            for ( int c = 0; c < jsonCategoryList.length (); c++ ) {
                JSONObject jsonSubcategory = jsonCategoryList.getJSONObject ( c );
                Ideogram subCategory = inflateLegacyJSONCategory ( jsonSubcategory );
                category.getChildren ( true ).add ( subCategory );
            }
        }

        if ( jsonCategory.has ( DataStore.JSON_IDEOGRAM_LIST ) ) {
            JSONArray jsonIdeogramList = jsonCategory.getJSONArray ( DataStore.JSON_IDEOGRAM_LIST );
            for ( int i = 0; i < jsonIdeogramList.length (); i++ ) {
                JSONObject jsonIdeogram = jsonIdeogramList.getJSONObject ( i );
                Ideogram word = new Ideogram ( Type.Word );
                word.setId ( getJSONString ( jsonIdeogram, DataStore.JSON_ID ) );
                word.setAudioExtention ( getJSONString ( jsonIdeogram, DataStore.JSON_AUDIO_EXT ) );
                word.setImageExtention ( getJSONString ( jsonIdeogram, DataStore.JSON_IMAGE_EXT ) );
                word.setLabel ( getJSONString ( jsonIdeogram, DataStore.JSON_LABEL ) );
                word.setPhrase ( getJSONString ( jsonIdeogram, DataStore.JSON_PHRASE ) );
                word.setParentId ( getJSONString ( jsonIdeogram, DataStore.JSON_PARENT_ID ) );
                word.setHidden ( getJSONBoolean ( jsonIdeogram, DataStore.JSON_HIDDEN ) );
                category.getChildren ( true ).add ( word );
                m_ideogramMap.put ( word.getId (), word );
            }
        }

        m_ideogramMap.put ( category.getId (), category );
        return category;
    }

    private String getJSONString ( JSONObject obj, String key ) {
        String value = "";
        if ( obj.has ( key ) ) {
            try {
                value = obj.getString ( key );
            } catch ( JSONException e ) {
                JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Failed to load JSON string from key: " + key );
            }
        }
        return value;
    }

    private boolean getJSONBoolean ( JSONObject obj, String key ) {
        boolean value = false;
        if ( obj.has ( key ) ) {
            try {
                value = obj.getBoolean ( key );
            } catch ( JSONException e ) {
                JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR,
                        "Failed to load JSON string from key: " + key );
            }
        }
        return value;
    }

    private List<Ideogram> getAllIdeogramsForCategory ( Ideogram category ) {
        List<Ideogram> ideogramList = new LinkedList<Ideogram> ();
        ideogramList.add ( category );
        for ( Ideogram child : category.getChildren ( true ) ) {
            if ( child.getType () == Type.Category ) {
                ideogramList.addAll ( getAllIdeogramsForCategory ( child ) );
            } else {
                ideogramList.add ( child );
            }
        }
        return ideogramList;
    }
    
    private List<Ideogram> getAllChildCategories ( Ideogram category ) {
        List<Ideogram> ideogramList = new LinkedList<Ideogram> ();
        ideogramList.add ( category );
        for ( Ideogram child : category.getChildren ( true ) ) {
            if ( child.getType () == Type.Category ) {
                ideogramList.addAll ( getAllIdeogramsForCategory ( child ) );
            }
        }
        return ideogramList;
    }    

    private void removeOrphans () {
        File[] audioList = getAudioDirectory ().listFiles ();
        File[] imageList = getImageDirectory ().listFiles ();

        for ( File f : audioList ) {
            String name = f.getName ();
            int ext = name.lastIndexOf ( "." );
            if ( ext > 0 ) {
                name = name.substring ( 0, ext );
            }
            if ( !m_ideogramMap.containsKey ( name )
                    || m_ideogramMap.get ( name ).isSynthesizeButton () ) {
                f.delete ();
            }
        }

        for ( File f : imageList ) {
            String name = f.getName ();
            int ext = name.lastIndexOf ( "." );
            if ( ext > 0 ) {
                name = name.substring ( 0, ext );
            }
            if ( !m_ideogramMap.containsKey ( name ) || m_ideogramMap.get ( name ).isTextButton () ) {
                f.delete ();
            }
        }
    }

    private String getFileExtention ( String fileName, boolean includeDot ) {
        int pos = fileName.lastIndexOf ( "." );
        if ( pos >= 0 ) {
            if ( includeDot ) {
                return fileName.substring ( pos );
            } else {
                return fileName.substring ( pos + 1 );
            }
        }
        return null;
    }

    public String getStackTrace ( Exception e ) {
        if ( e.getMessage () != null ) {
            return e.getMessage ();
        }

        StringWriter sw = new StringWriter ();
        PrintWriter pw = new PrintWriter ( sw );
        e.printStackTrace ( pw );
        pw.flush ();
        return sw.toString ();
    }

}
