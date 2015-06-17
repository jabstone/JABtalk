package com.jabstone.jabtalk.basic;

import com.jabstone.jabtalk.basic.activity.MainActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class HomeReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent i = new Intent (context, MainActivity.class );
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(i);
	}

}
