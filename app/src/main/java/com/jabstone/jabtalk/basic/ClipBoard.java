package com.jabstone.jabtalk.basic;


import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.listeners.IDataStoreListener;


public class ClipBoard implements IDataStoreListener {

    private String sourceId = null;
    private Operation operation = null;

    public ClipBoard () {
        JTApp.addDataStoreListener ( this );
    }

    public enum Operation {
        COPY, CUT;
    }

    public String getId () {
        return sourceId;
    }

    public void setId ( String id ) {
        this.sourceId = id;
    }

    public Operation getOperation () {
        return operation;
    }

    public void setOperation ( Operation operation ) {
        this.operation = operation;
    }

    public void paste ( String targetId ) throws JabException {
        if ( operation == null || targetId == null ) {
            return;
        }

        try {
            switch ( operation ) {
                case COPY:
                    JTApp.getDataStore ().copyIdeogram ( sourceId, targetId );
                    break;
                case CUT:
                    JTApp.getDataStore ().cutIdeogram ( sourceId, targetId );
                    sourceId = null;
                    operation = null;
                    break;
            }
        } catch ( JabException je ) {
            clear ();
            JTApp.getDataStore ().refreshStore ();
            throw je;
        }
    }

    public boolean isEmpty () {
        if ( sourceId != null ) {
            return false;
        } else {
            return true;
        }
    }

    public void clear () {
        sourceId = null;
        operation = null;
    }

    public void DataStoreUpdated () {
        if ( sourceId != null && JTApp.getDataStore ().getIdeogram ( sourceId ) == null ) {
            clear ();
        }

    }

}
