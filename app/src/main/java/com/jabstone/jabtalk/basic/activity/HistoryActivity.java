package com.jabstone.jabtalk.basic.activity;

import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.JTApp;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

public class HistoryActivity extends Activity {
	private TextView logView = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.history_activity);
		logView = (TextView)findViewById(R.id.historyText);		
		logView.setText(JTApp.getHistory());
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(logView != null) {
			logView.setText(JTApp.getHistory());
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.history_menu, menu);
	    return true;
	}	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {		
		switch (item.getItemId()) {
			case R.id.menu_item_clear_history:
				JTApp.clearHistory();
				logView.setText("");
				break;		
		}
		
		return true;
	}			
}
