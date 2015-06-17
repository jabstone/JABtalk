package com.jabstone.jabtalk.basic.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.listeners.IDataStoreListener;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;
import com.jabstone.jabtalk.basic.widgets.AutoResizeTextView;

public class ManageParentListAdapter extends BaseAdapter implements
		IDataStoreListener {

	private static String TAG = ManageParentListAdapter.class.getSimpleName();
	private Context m_context;
	private Ideogram m_ideogram = null;
	private OnClickListener m_clickListener = null;

	public ManageParentListAdapter(Context context, Ideogram ideogram) {
		m_context = context;
		m_ideogram = ideogram;
	}

	public int getCount() {
		return m_ideogram.getChildren(true).size();
	}

	public Object getItem(int position) {
		return null;
	}

	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		final Ideogram gram = m_ideogram.getChildren(true).get(position);
		Bitmap jpg = gram.getImage();
		if (jpg.getWidth() > jpg.getHeight()) {
			if (gram.getType().equals(Type.Category)) {
				return 0;
			} else {
				return 1;
			}
		} else {
			if (gram.getType().equals(Type.Category)) {
				return 2;
			} else {
				return 3;
			}
		}
	}

	@Override
	public int getViewTypeCount() {
		return 4;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		RelativeLayout ideogramLayout = null;
		Ideogram ideogram = m_ideogram.getChildren(true).get(position);
		Bitmap jpg = ideogram.getImage();
		if (convertView == null) {
			ideogramLayout = new RelativeLayout(m_context);
			LayoutInflater inflater = (LayoutInflater) m_context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			if (jpg.getWidth() > jpg.getHeight()) {
				if (ideogram.getType().equals(Type.Category)) {
					convertView = inflater
							.inflate(
									com.jabstone.jabtalk.basic.R.layout.category_listitem_landscape,
									ideogramLayout, true);
				} else {
					convertView = inflater
							.inflate(
									com.jabstone.jabtalk.basic.R.layout.word_listitem_landscape,
									ideogramLayout, true);
				}
			} else {

				if (ideogram.getType().equals(Type.Category)) {
					convertView = inflater
							.inflate(
									com.jabstone.jabtalk.basic.R.layout.category_listitem_portrait,
									ideogramLayout, true);
				} else {
					convertView = inflater
							.inflate(
									com.jabstone.jabtalk.basic.R.layout.word_listitem_portrait,
									ideogramLayout, true);
				}
			}
			convertView.setOnClickListener(m_clickListener);
			ideogramLayout = (RelativeLayout) convertView
					.findViewById(R.id.ideogram_listitem_layout);
			ideogramLayout.setOnClickListener(m_clickListener);
			((Activity) m_context).registerForContextMenu(ideogramLayout);
		} else {
			ideogramLayout = (RelativeLayout) convertView
					.findViewById(R.id.ideogram_listitem_layout);
		}

		try {
			ImageView thumb = (ImageView) convertView
					.findViewById(com.jabstone.jabtalk.basic.R.id.ideogram_thumb);
			TextView label = (TextView) convertView
					.findViewById(com.jabstone.jabtalk.basic.R.id.ideogram_label);
			TextView phrase = (TextView) convertView
					.findViewById(com.jabstone.jabtalk.basic.R.id.ideogram_phrase);
			thumb.setImageBitmap(jpg);
			label.setText(ideogram.getLabel());
			
			phrase.setText(ideogram.getPhrase() != null ? Html.fromHtml("<i>"
					+ ideogram.getPhrase() + "&nbsp;</i>") : "");
			ideogramLayout.setTag(ideogram);
			AutoResizeTextView textLabel = (AutoResizeTextView) convertView
					.findViewById(R.id.ideogram_text_label);
			if (ideogram.isHidden()) {
				label.setTextColor(m_context.getResources().getColor(
						com.jabstone.jabtalk.basic.R.color.jabtalkRed));
				phrase.setTextColor(m_context.getResources().getColor(
						com.jabstone.jabtalk.basic.R.color.jabtalkRed));
				phrase.setText(Html
						.fromHtml("<i>"
								+ m_context
										.getResources()
										.getString(
												com.jabstone.jabtalk.basic.R.string.manage_activity_hidden_item)
								+ "&nbsp;</i>"));
			} else {
				label.setTextColor(m_context.getResources().getColor(
						com.jabstone.jabtalk.basic.R.color.jabtalkBlack));
				phrase.setTextColor(m_context.getResources().getColor(
						com.jabstone.jabtalk.basic.R.color.jabtalkBlack));
			}
			if (ideogram.isTextButton()) {
				int thumbWidth = m_context.getResources()
						.getDimensionPixelSize(
								R.dimen.image_thumb_landscape_width);
				int thumbHeight = m_context.getResources()
						.getDimensionPixelSize(
								R.dimen.image_thumb_landscape_height);
				textLabel.setTextSize(50);
				textLabel.setText(ideogram.getLabel());
				textLabel.resizeText(
						(int) (thumbWidth * JTApp.TEXT_BUTTON_PADDING),
						(int) (thumbHeight * JTApp.TEXT_BUTTON_PADDING));
				textLabel.setVisibility(View.VISIBLE);
			} else {
				textLabel.setVisibility(View.GONE);
			}

		} catch (Exception fnf) {
			JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, fnf.getMessage());
		}
		return convertView;
	}

	public void setOnClickListener(View.OnClickListener listener) {
		this.m_clickListener = listener;
	}

	@Override
	public void DataStoreUpdated() {
		Ideogram g = JTApp.getDataStore().getIdeogram(m_ideogram.getId());
		if(g != null && !g.equals(m_ideogram)) {
			m_ideogram = JTApp.getDataStore().getRootCategory();
		}
		notifyDataSetChanged();
	}
}
