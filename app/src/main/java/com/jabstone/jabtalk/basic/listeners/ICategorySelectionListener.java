package com.jabstone.jabtalk.basic.listeners;

import java.util.EventListener;

import com.jabstone.jabtalk.basic.storage.Ideogram;

public interface ICategorySelectionListener extends EventListener {
	void CategorySelected(Ideogram category, boolean touchEvent);
}
