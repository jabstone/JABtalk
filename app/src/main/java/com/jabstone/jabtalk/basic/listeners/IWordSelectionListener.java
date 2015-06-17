package com.jabstone.jabtalk.basic.listeners;

import java.util.EventListener;

import com.jabstone.jabtalk.basic.storage.Ideogram;

public interface IWordSelectionListener extends EventListener {
	void WordSelected(Ideogram gram, boolean isSentenceWord);
}
