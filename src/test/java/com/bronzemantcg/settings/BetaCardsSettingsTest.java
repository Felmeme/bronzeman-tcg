package com.bronzemantcg.settings;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.ActionMap;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BetaCardsSettingsTest
{
	@Test
	public void pickerStartsLargerAndInDetailsView() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JFileChooser chooser = new JFileChooser();
			BetaCardsSettings.configureChooser(chooser);
			assertEquals(new Dimension(850, 600), chooser.getPreferredSize());
			assertTrue("The standard chooser should contain its Details table", containsTable(chooser));
		});
	}

	@Test
	public void unfamiliarLookAndFeelKeepsWorkingWithoutDetailsAction() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JFileChooser chooser = new JFileChooser();
			chooser.setActionMap(new ActionMap());
			BetaCardsSettings.configureChooser(chooser);
			assertEquals(new Dimension(850, 600), chooser.getPreferredSize());
		});
	}

	private static boolean containsTable(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JTable || child instanceof Container && containsTable((Container) child))
			{
				return true;
			}
		}
		return false;
	}
}
