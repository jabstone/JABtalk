package com.jabstone.jabtalk.basic.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class JTLinearLayout extends LinearLayout {
	private IFrameResizeListener m_listener = null;

	public JTLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public JTLinearLayout(Context context) {
		super(context);
	}
	
	public void setOnFrameResizeListener(IFrameResizeListener listener) {
		m_listener = listener;
	}	

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if(m_listener != null) {
			m_listener.OnFrameResized(w, h);
		}
	}	

	
}
