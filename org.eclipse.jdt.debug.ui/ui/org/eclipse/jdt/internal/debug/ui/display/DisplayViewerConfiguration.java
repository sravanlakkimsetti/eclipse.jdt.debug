package org.eclipse.jdt.internal.debug.ui.display;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.internal.debug.ui.JDIViewerConfiguration;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;

/**
 *  The source viewer configuration for the Display view
 */
public class DisplayViewerConfiguration extends JDIViewerConfiguration {
		
	public DisplayViewerConfiguration() {
		super(JavaPlugin.getDefault().getJavaTextTools());
	}

	/**
	 * @see JDIViewerConfiguration#getContentAssistantProcessor()
	 */
	public IContentAssistProcessor getContentAssistantProcessor() {
		return new DisplayCompletionProcessor();
	}

}