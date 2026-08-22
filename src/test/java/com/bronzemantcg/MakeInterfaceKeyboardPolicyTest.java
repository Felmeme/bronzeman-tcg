package com.bronzemantcg;

import com.google.gson.Gson;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class MakeInterfaceKeyboardPolicyTest
{
	@Test
	public void mapsAllCurrentSkillMultiProductSlots()
	{
		Assert.assertEquals('1', MakeInterfaceKeyboardPolicy.shortcutForIndex(0));
		Assert.assertEquals('9', MakeInterfaceKeyboardPolicy.shortcutForIndex(8));
		Assert.assertEquals('0', MakeInterfaceKeyboardPolicy.shortcutForIndex(9));
		Assert.assertEquals('A', MakeInterfaceKeyboardPolicy.shortcutForIndex(10));
		Assert.assertEquals('H', MakeInterfaceKeyboardPolicy.shortcutForIndex(17));
		Assert.assertEquals('\0', MakeInterfaceKeyboardPolicy.shortcutForIndex(18));
	}

	@Test
	public void mapsTopRowNumpadAndLetterKeyEvents()
	{
		Assert.assertEquals('2',
			MakeInterfaceKeyboardPolicy.shortcutForKeyCode(KeyEvent.VK_2));
		Assert.assertEquals('2',
			MakeInterfaceKeyboardPolicy.shortcutForKeyCode(KeyEvent.VK_NUMPAD2));
		Assert.assertEquals('A',
			MakeInterfaceKeyboardPolicy.shortcutForKeyCode(KeyEvent.VK_A));
		Assert.assertEquals('A', MakeInterfaceKeyboardPolicy.shortcutForCharacter('a'));
		Assert.assertEquals('\0',
			MakeInterfaceKeyboardPolicy.shortcutForKeyCode(KeyEvent.VK_ENTER));
	}

	@Test
	public void recognizesOnlyTheActualQuantityPrompt()
	{
		Assert.assertTrue(MakeInterfaceKeyboardPolicy.isQuantityAmountPrompt(
			"Enter amount (1 - 28):"));
		Assert.assertTrue(MakeInterfaceKeyboardPolicy.isQuantityAmountPrompt(
			"<col=ffffff>Enter amount (1 - 28):</col>"));
		Assert.assertFalse(MakeInterfaceKeyboardPolicy.isQuantityAmountPrompt(
			"Choose a quantity, then click an item."));
	}

	@Test
	public void salmonShortcutUsesTheExistingCookingInterfaceRequirement()
	{
		ResourceNodeCatalog.Rule rule = new ResourceNodeCatalog(new Gson()).find(
			ResourceNodeCatalog.KIND_INTERFACE, "Salmon", ResourceNodeCatalog.ANY_OPTION);
		Assert.assertNotNull(rule);
		Assert.assertEquals(List.of("Raw salmon"), rule.missingRequirements(
			Collections.emptySet(), Collections.singleton("output"), false));
	}
}
