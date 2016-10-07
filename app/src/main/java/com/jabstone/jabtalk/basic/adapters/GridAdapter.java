package com.jabstone.jabtalk.basic.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.PictureSize;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.listeners.ICategorySelectionListener;
import com.jabstone.jabtalk.basic.listeners.IDataStoreListener;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;
import com.jabstone.jabtalk.basic.widgets.AutoResizeTextView;
import com.jabstone.jabtalk.basic.widgets.PictureFrameDimensions;


public class GridAdapter extends BaseAdapter implements ICategorySelectionListener, IDataStoreListener {

    private final String TAG = GridAdapter.class.getSimpleName ();
    private Context m_context;
    private Ideogram m_selectedCategory = null;
    private boolean longClicked = false;

    public GridAdapter ( Context context ) {
        m_context = context;
        m_selectedCategory = JTApp.getDataStore().getRootCategory();
    }

    @Override
    public int getCount () {
        int count = 0;
        if ( m_selectedCategory != null
                && JTApp.getDataStore ().getIdeogram ( m_selectedCategory.getId () ) != null ) {
            count = m_selectedCategory.getChildren(false).size ();
        } else {
            m_selectedCategory = JTApp.getDataStore().getRootCategory();
        }
        return count;
    }

    @Override
    public Object getItem ( int position ) {
        return null;
    }

    @Override
    public long getItemId ( int position ) {
        return 0;
    }


    @Override
    public View getView ( int position, View convertView, ViewGroup parent ) {
        RelativeLayout pictureView;
        Ideogram gram = m_selectedCategory.getChildren(false).get ( position );

        if ( convertView == null || !convertView.getTag ().equals ( JTApp.getPictureVersion () ) ) {
            pictureView = new RelativeLayout ( m_context );
            switch ( gram.getType () ) {
                case Category:
                    pictureView = JTApp.getPictureLayout ( m_context, Type.Category,
                            PictureSize.GridPicture, gram.isTextButton () );
                    break;
                case Word:
                    pictureView = JTApp.getPictureLayout ( m_context, Type.Word,
                            PictureSize.GridPicture, gram.isTextButton () );
                    break;
            }

        } else {
            pictureView = ( RelativeLayout ) convertView;
        }

        try {

            Bitmap jpg = gram.getImage ();
            ImageView picture = (ImageView) pictureView.findViewById(R.id.IMAGEVIEW_ID);
            AutoResizeTextView title = ( AutoResizeTextView ) pictureView
                    .findViewById(R.id.TEXTVIEW_ID);
            picture.setImageBitmap ( jpg );
            pictureView.setTag ( gram );
            title.setText ( gram.getLabel () );            
            if ( gram.isTextButton () ) {
                PictureFrameDimensions dm = JTApp.getPictureDimensions ( PictureSize.GridPicture,
                        true );
                title.resizeText ( dm.getWidth (), dm.getHeight () );
            } else {
            	title.setTextSize(TypedValue.COMPLEX_UNIT_SP, JTApp.getTitleFontSize());
            }

            final Ideogram word = gram;
            pictureView.setOnClickListener ( new View.OnClickListener () {

                public void onClick ( View v ) {
                    if ( !longClicked ) {
                        selectItem ( v, word );
                    } else {
                        longClicked = false;
                    }
                }
            } );

            pictureView.setOnLongClickListener ( new View.OnLongClickListener () {

                public boolean onLongClick ( View v ) {
                    longClicked = true;
                    selectItem ( v, word );
                    return true;
                }
            } );

        } catch ( Throwable fnf ) {
            JTApp.logMessage ( TAG, JTApp.LOG_SEVERITY_ERROR, "Error creating view. Message is: "
                    + fnf.getMessage () );
        }
        return pictureView;
    }
    
    @Override
    public void CategorySelected ( Ideogram category, boolean touchEvent ) {
        m_selectedCategory = category;
        this.notifyDataSetChanged ();
    }    
    
    @Override
	public void DataStoreUpdated() {
		m_selectedCategory = JTApp.getDataStore ().getRootCategory();		
	}    

    private void selectItem ( View v, Ideogram gram ) {
        if ( !JTApp.isAudioPlaying () || (gram.getType().equals(Type.Category) && 
        		(!JTApp.isCategoryAudioEnabled() || gram.getAudioPath() == null))) {
            v.performHapticFeedback ( HapticFeedbackConstants.LONG_PRESS );
            if ( gram.getType () == Type.Word ) {
                JTApp.fireWordSelected ( gram, false );
            } else {
                JTApp.fireCategorySelected ( gram, true );
            }
        }
    }

}
