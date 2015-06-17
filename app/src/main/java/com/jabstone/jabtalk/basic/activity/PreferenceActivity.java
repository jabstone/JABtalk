package com.jabstone.jabtalk.basic.activity;

import com.jabstone.jabtalk.basic.R;

import android.os.Bundle;

public class PreferenceActivity extends android.preference.PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);	
	}

	@Override
	public void onBackPressed() {	
		setResult(RESULT_OK, getIntent());
		finish();		
	}	
	
}
