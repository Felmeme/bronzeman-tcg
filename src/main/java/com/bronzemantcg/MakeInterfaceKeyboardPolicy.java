package com.bronzemantcg;

import java.awt.event.KeyEvent;
import java.util.Locale;

/** Pure keyboard mapping for the standard 18-slot SkillMulti interface. */
final class MakeInterfaceKeyboardPolicy
{
	private static final String SHORTCUTS = "1234567890ABCDEFGH";

	private MakeInterfaceKeyboardPolicy()
	{
	}

	static char shortcutForIndex(int index)
	{
		return index >= 0 && index < SHORTCUTS.length() ? SHORTCUTS.charAt(index) : '\0';
	}

	static char shortcutForKeyCode(int keyCode)
	{
		if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
		{
			return (char) ('0' + keyCode - KeyEvent.VK_0);
		}
		if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9)
		{
			return (char) ('0' + keyCode - KeyEvent.VK_NUMPAD0);
		}
		if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_H)
		{
			return (char) ('A' + keyCode - KeyEvent.VK_A);
		}
		return '\0';
	}

	static char shortcutForCharacter(char keyChar)
	{
		return Character.toUpperCase(keyChar);
	}

	static boolean isQuantityAmountPrompt(String value)
	{
		return normalize(value).startsWith("enter amount (");
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.replaceAll("<[^>]*>", "")
			.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
