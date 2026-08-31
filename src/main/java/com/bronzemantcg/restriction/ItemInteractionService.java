package com.bronzemantcg.restriction;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.catalog.RecipeCatalog;
import com.bronzemantcg.catalog.ResourceNodeCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/** Owns all non-NPC menu-visibility and click-routing decisions. */
@Slf4j
@Singleton
public final class ItemInteractionService
{
	private static final String TAKE_OPTION = "take";
	private static final String USED_ON_SEPARATOR = " -> ";
	private static final String CAST_PREFIX = "Cast ";
	private static final int MAKE_MATERIAL_MEMORY_TICKS = 100;
	private static final Set<String> FORCED_DROP_ALLOWED = Set.of("drop", "destroy");
	private static final Set<String> MAKE_VERBS = Set.of(
		"smelt", "make", "make-x", "make-all", "make sets", "craft", "smith",
		"string", "mix", "cook", "bake", "fletch", "spin", "fire");

	private final Client client;
	private final BronzemanTcgConfig config;
	private final ItemManager itemManager;
	private final RestrictionDecisionService restrictionDecisionService;
	private final ResourceRestrictionService resourceRestrictionService;
	private final RecipeRestrictionService recipeRestrictionService;
	private final ResourceNodeCatalog nodeCatalog;
	private final RecipeCatalog recipeCatalog;

	private String lastUsedItemA;
	private String lastUsedItemB;
	private int lastUsedItemTick = Integer.MIN_VALUE;

	@Inject
	public ItemInteractionService(Client client, BronzemanTcgConfig config, ItemManager itemManager,
		RestrictionDecisionService restrictionDecisionService,
		ResourceRestrictionService resourceRestrictionService,
		RecipeRestrictionService recipeRestrictionService, ResourceNodeCatalog nodeCatalog,
		RecipeCatalog recipeCatalog)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.restrictionDecisionService = restrictionDecisionService;
		this.resourceRestrictionService = resourceRestrictionService;
		this.recipeRestrictionService = recipeRestrictionService;
		this.nodeCatalog = nodeCatalog;
		this.recipeCatalog = recipeCatalog;
	}

	/** Hot MenuEntryAdded decision for every non-NPC route. */
	public boolean shouldHideMenuEntry(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}
		MenuAction type = entry.getType();
		String option = clean(entry.getOption());
		if (option.isEmpty())
		{
			return false;
		}
		String optionLower = option.toLowerCase(Locale.ROOT);
		int menuGroup = WidgetUtil.componentToInterface(entry.getParam1());
		if (isInventoryMenuVisibilityExempt(type, menuGroup))
		{
			return false;
		}

		switch (type)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				if (config.groundItemsMode() != LockState.LOCKED
					|| !TAKE_OPTION.equals(optionLower))
				{
					return false;
				}
				return isLockedItem(entry.getIdentifier(), itemName(entry.getIdentifier()));
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			{
				String objectName = clean(entry.getTarget());
				if (objectName.isEmpty())
				{
					return false;
				}
				ResourceNodeCatalog.Rule rule = nodeCatalog.find(ResourceNodeCatalog.KIND_OBJECT,
					objectName, option, entry.getIdentifier());
				return rule != null && shouldHideWorldObjectCategory(rule.category)
					&& hasMissing(resourceRestrictionService.evaluate(ResourceNodeCatalog.KIND_OBJECT,
						objectName, option, entry.getIdentifier()));
			}
			case WIDGET_TARGET:
				if (config.itemUsageMode() != LockState.LOCKED
					|| menuGroup != InterfaceID.INVENTORY)
				{
					return false;
				}
				String usedItem = clean(entry.getTarget());
				return !usedItem.isEmpty() && isLockedItem(
					entry.getItemId() > 0 ? entry.getItemId() : -1, usedItem);
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
				if (isShopBuyOption(menuGroup, optionLower))
				{
					return entry.getItemId() > 0
						&& isShopItemLocked(entry.getItemId(), itemName(entry.getItemId()), true);
				}
				if (isShopSellOption(menuGroup, optionLower))
				{
					return entry.getItemId() > 0
						&& isShopItemLocked(entry.getItemId(), itemName(entry.getItemId()), false);
				}
				if (!entry.isItemOp() || entry.getItemId() <= 0
					|| menuGroup != InterfaceID.INVENTORY)
				{
					return false;
				}
				String itemName = itemName(entry.getItemId());
				if (itemName == null || itemName.isEmpty())
				{
					return false;
				}
				if (hasMissing(resourceRestrictionService.evaluate(
					ResourceNodeCatalog.KIND_INVENTORY, itemName, option)))
				{
					return true;
				}
				return requiresInventoryCard(config.itemUsageMode(),
					config.foodSettingsMode(), optionLower)
					&& isLockedItem(entry.getItemId(), itemName);
			default:
				return false;
		}
	}

	/** PostMenuSort inventory decision; mutation remains in the plugin subscriber. */
	public boolean shouldHideInventoryEntryAfterSort(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}
		MenuAction type = entry.getType();
		int menuGroup = WidgetUtil.componentToInterface(entry.getParam1());
		if (!isInventoryMenuVisibilityExempt(type, menuGroup))
		{
			return false;
		}
		String option = clean(entry.getOption());
		if (option.isEmpty())
		{
			return false;
		}
		if (type == MenuAction.WIDGET_TARGET)
		{
			String itemName = clean(entry.getTarget());
			return requiresInventoryCard(config.itemUsageMode(),
				config.foodSettingsMode(), option)
				&& !itemName.isEmpty() && isLockedItem(
					entry.getItemId() > 0 ? entry.getItemId() : -1, itemName);
		}
		if (!entry.isItemOp() || entry.getItemId() <= 0)
		{
			return false;
		}
		String itemName = itemName(entry.getItemId());
		if (itemName == null || itemName.isEmpty())
		{
			return false;
		}
		if (hasMissing(resourceRestrictionService.evaluate(
			ResourceNodeCatalog.KIND_INVENTORY, itemName, option)))
		{
			return true;
		}
		return requiresInventoryCard(config.itemUsageMode(),
			config.foodSettingsMode(), option)
			&& isLockedItem(entry.getItemId(), itemName);
	}

	/** Routes one non-NPC click and returns only the effect the plugin must apply. */
	public InteractionDecision evaluateInteraction(MenuOptionClicked event)
	{
		if (event == null || event.getMenuAction() == null)
		{
			return InteractionDecision.allowed();
		}
		switch (event.getMenuAction())
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GROUND_ITEM:
				return evaluateGroundItem(event);
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return evaluateGameObject(event);
			case WIDGET_TARGET_ON_GAME_OBJECT:
				return evaluateItemOnGameObject(event);
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
				return evaluateWidgetOp(event);
			case WIDGET_TARGET:
				return evaluateUseSelected(event);
			case WIDGET_TARGET_ON_WIDGET:
				return evaluateWidgetOnWidget(event);
			default:
				return InteractionDecision.allowed();
		}
	}

	private InteractionDecision evaluateGroundItem(MenuOptionClicked event)
	{
		if (!isGroundInteractionRestricted(config.groundItemsMode(),
			event.getMenuAction(), event.getMenuOption()))
		{
			return InteractionDecision.allowed();
		}
		String itemName = itemName(event.getId());
		return blockedItem(event.getId(), itemName);
	}

	private InteractionDecision evaluateGameObject(MenuOptionClicked event)
	{
		if (event.getMenuOption() == null)
		{
			return InteractionDecision.allowed();
		}
		String objectName = clean(event.getMenuTarget());
		String option = clean(event.getMenuOption());
		if (log.isDebugEnabled() && (objectName.toLowerCase(Locale.ROOT).contains("shipwreck")
			|| objectName.toLowerCase(Locale.ROOT).contains("salvaging hook")
			|| objectName.equalsIgnoreCase("Boat schematics")))
		{
			log.debug("sailing object click id={} name='{}' option='{}' action={}",
				event.getId(), objectName, option, event.getMenuAction());
		}
		return evaluateNode(ResourceNodeCatalog.KIND_OBJECT, objectName, option, event.getId());
	}

	private InteractionDecision evaluateItemOnGameObject(MenuOptionClicked event)
	{
		String[] pair = splitUsedOn(event.getMenuTarget());
		if (pair == null)
		{
			return InteractionDecision.allowed();
		}
		InteractionDecision node = evaluateNode(
			ResourceNodeCatalog.KIND_ITEM_ON_OBJECT, pair[0], pair[1], -1);
		return node.isBlocked() ? node
			: evaluateRecipe(RecipeCatalog.KIND_ITEM_ON_OBJECT, pair[0], pair[1]);
	}

	private InteractionDecision evaluateWidgetOp(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		String option = clean(event.getMenuOption());
		String optionLower = option.toLowerCase(Locale.ROOT);
		int group = WidgetUtil.componentToInterface(entry.getParam1());

		if (group == InterfaceID.SAILING_MENU || group == InterfaceID.SAILING_CUSTOMISATION)
		{
			Widget widget = event.getWidget();
			String itemName = widget != null && widget.getItemId() > 0
				? itemName(widget.getItemId()) : null;
			logSailingInterface(group, widget, option, event.getMenuTarget(), itemName);
			if (itemName != null && !itemName.isEmpty()
				&& nodeCatalog.find(ResourceNodeCatalog.KIND_INTERFACE, itemName,
					ResourceNodeCatalog.ANY_OPTION) != null)
			{
				return evaluateNode(ResourceNodeCatalog.KIND_INTERFACE, itemName,
					ResourceNodeCatalog.ANY_OPTION, -1);
			}
		}

		if (optionLower.startsWith("withdraw") || optionLower.startsWith("deposit"))
		{
			return isBankInteractionRestricted(config.itemUsageMode(), config.bankingMode(), optionLower)
				? blockedItem(itemOpId(event, entry), itemOpName(event, entry))
				: InteractionDecision.allowed();
		}
		if (group == InterfaceID.SHOPMAIN)
		{
			return isShopBuyOption(group, optionLower)
				? evaluateShopItem(itemOpId(event, entry), itemOpName(event, entry), true)
				: InteractionDecision.allowed();
		}
		if (group == InterfaceID.SHOPSIDE)
		{
			return isShopSellOption(group, optionLower)
				? evaluateShopItem(itemOpId(event, entry), itemOpName(event, entry), false)
				: InteractionDecision.allowed();
		}
		if (group == InterfaceID.SKILLMULTI || group == InterfaceID.SMITHING)
		{
			return evaluateInterfaceProduct(event);
		}
		if (group == InterfaceID.INVENTORY)
		{
			return evaluateInventoryOp(event, entry, option, optionLower);
		}
		if (config.grandExchangeMode() == LockState.LOCKED
			&& group == InterfaceID.CHATBOX && isGrandExchangeOpen())
		{
			String targetName = clean(event.getMenuTarget());
			InteractionDecision blocked = blockedItem(-1, targetName);
			if (blocked.isBlocked())
			{
				return blocked;
			}
		}
		return isMakeVerb(optionLower)
			? evaluateInterfaceProduct(event) : InteractionDecision.allowed();
	}

	private InteractionDecision evaluateInterfaceProduct(MenuOptionClicked event)
	{
		String product = stripProductQuantity(clean(event.getMenuTarget()));
		logInterfaceProduct(event, product);
		if (product.isEmpty())
		{
			return InteractionDecision.allowed();
		}
		InteractionDecision node = evaluateNode(ResourceNodeCatalog.KIND_INTERFACE,
			product, ResourceNodeCatalog.ANY_OPTION, -1);
		return node.isBlocked() ? node : evaluateRecipe(
			RecipeCatalog.KIND_INTERFACE, product, resolveInterfaceMaterial(product));
	}

	private InteractionDecision evaluateInventoryOp(MenuOptionClicked event, MenuEntry entry,
		String option, String optionLower)
	{
		String itemName = itemOpName(event, entry);
		if (itemName == null || itemName.isEmpty())
		{
			return InteractionDecision.allowed();
		}
		InteractionDecision node = evaluateNode(
			ResourceNodeCatalog.KIND_INVENTORY, itemName, option, -1);
		if (node.isBlocked())
		{
			return node;
		}
		if ("light".equals(optionLower))
		{
			InteractionDecision fire = evaluateRecipe(
				RecipeCatalog.KIND_ITEM_ON_ITEM, "Tinderbox", itemName);
			if (fire.isBlocked())
			{
				return fire;
			}
		}
		return requiresInventoryCard(config.itemUsageMode(),
			config.foodSettingsMode(), optionLower)
			? blockedItem(itemOpId(event, entry), itemName) : InteractionDecision.allowed();
	}

	private InteractionDecision evaluateUseSelected(MenuOptionClicked event)
	{
		if (config.itemUsageMode() != LockState.LOCKED
			|| WidgetUtil.componentToInterface(event.getMenuEntry().getParam1())
				!= InterfaceID.INVENTORY)
		{
			return InteractionDecision.allowed();
		}
		MenuEntry entry = event.getMenuEntry();
		return blockedItem(itemOpId(event, entry), clean(event.getMenuTarget()));
	}

	private InteractionDecision evaluateWidgetOnWidget(MenuOptionClicked event)
	{
		String[] pair = splitUsedOn(event.getMenuTarget());
		if (pair == null)
		{
			return InteractionDecision.allowed();
		}
		String source = pair[0];
		String destination = pair[1];
		rememberMaterialPair(source, destination, client.getTickCount());

		if (source.startsWith(CAST_PREFIX) || isSelectedWidgetSpell())
		{
			return isSelectedEnchantSpell(source) && config.restrictEnchanting()
				? evaluateRecipe(RecipeCatalog.KIND_SPELL_ON_ITEM, destination, null)
				: InteractionDecision.allowed();
		}
		InteractionDecision recipe = evaluateRecipe(
			RecipeCatalog.KIND_ITEM_ON_ITEM, source, destination);
		if (!recipe.isBlocked())
		{
			recipe = evaluateRecipe(RecipeCatalog.KIND_ITEM_ON_ITEM, destination, source);
		}
		if (recipe.isBlocked() || config.itemUsageMode() != LockState.LOCKED)
		{
			return recipe;
		}

		Widget selected = client.getSelectedWidget();
		Widget destinationWidget = event.getWidget();
		int sourceId = selected != null && selected.getItemId() > 0
			? selected.getItemId() : -1;
		int destinationId = destinationWidget != null && destinationWidget.getItemId() > 0
			? destinationWidget.getItemId() : -1;
		InteractionDecision sourceBlock = blockedItem(sourceId, source);
		return sourceBlock.isBlocked() ? sourceBlock : blockedItem(destinationId, destination);
	}

	private InteractionDecision evaluateNode(String kind, String name, String option, int targetId)
	{
		if (log.isDebugEnabled())
		{
			ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option, targetId);
			log.debug("node lookup kind={} name='{}' option='{}' targetId={} -> {}",
				kind, name, option, targetId,
				rule == null ? "NO RULE" : "rule[" + rule.category + "]");
		}
		return InteractionDecision.blockedCards(
			resourceRestrictionService.evaluate(kind, name, option, targetId));
	}

	private InteractionDecision evaluateRecipe(String kind, String name, String target)
	{
		return InteractionDecision.blockedCards(
			recipeRestrictionService.evaluate(kind, name, target));
	}

	private InteractionDecision blockedItem(int itemId, String itemName)
	{
		return isLockedItem(itemId, itemName)
			? InteractionDecision.blockedItem(itemName) : InteractionDecision.allowed();
	}

	/** Buying requires Coins and the item parent; selling requires only the item parent. */
	InteractionDecision evaluateShopItem(int itemId, String itemName, boolean buying)
	{
		List<String> missing = new ArrayList<>();
		if (buying)
		{
			addMissing(missing,
				restrictionDecisionService.missingItemCardName(-1, "Coins"));
		}
		addMissing(missing,
			restrictionDecisionService.missingItemCardName(itemId, itemName));
		return InteractionDecision.blockedCards(missing);
	}

	private boolean isShopItemLocked(int itemId, String itemName, boolean buying)
	{
		return (buying && isLockedItem(-1, "Coins")) || isLockedItem(itemId, itemName);
	}

	private static void addMissing(List<String> missing, String cardName)
	{
		if (cardName != null && !missing.contains(cardName))
		{
			missing.add(cardName);
		}
	}

	private boolean isLockedItem(int itemId, String itemName)
	{
		return itemName != null && !itemName.isEmpty()
			&& restrictionDecisionService.isItemLocked(itemId, itemName);
	}

	private String itemName(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition == null ? null : composition.getName();
	}

	private String itemOpName(MenuOptionClicked event, MenuEntry entry)
	{
		int itemId = itemOpId(event, entry);
		return itemId > 0 ? itemName(itemId) : clean(event.getMenuTarget());
	}

	private static int itemOpId(MenuOptionClicked event, MenuEntry entry)
	{
		return event.getItemId() > 0 ? event.getItemId()
			: entry.getItemId() > 0 ? entry.getItemId() : -1;
	}

	static String[] splitUsedOn(String target)
	{
		String cleanTarget = clean(target);
		int separator = cleanTarget.lastIndexOf(USED_ON_SEPARATOR);
		if (separator < 0)
		{
			return null;
		}
		return new String[]{cleanTarget.substring(0, separator).trim(),
			cleanTarget.substring(separator + USED_ON_SEPARATOR.length()).trim()};
	}

	void rememberMaterialPair(String first, String second, int tick)
	{
		lastUsedItemA = first;
		lastUsedItemB = second;
		lastUsedItemTick = tick;
	}

	String resolveInterfaceMaterial(String product, int currentTick)
	{
		if (currentTick - lastUsedItemTick > MAKE_MATERIAL_MEMORY_TICKS)
		{
			return null;
		}
		for (String candidate : new String[]{lastUsedItemA, lastUsedItemB})
		{
			if (candidate != null && !candidate.isEmpty()
				&& recipeCatalog.findExact(RecipeCatalog.KIND_INTERFACE, product, candidate) != null)
			{
				return candidate;
			}
		}
		return null;
	}

	private String resolveInterfaceMaterial(String product)
	{
		return resolveInterfaceMaterial(product, client.getTickCount());
	}

	private boolean isSelectedWidgetSpell()
	{
		if (!client.isWidgetSelected())
		{
			return false;
		}
		Widget selected = client.getSelectedWidget();
		return selected != null && selected.getItemId() <= 0;
	}

	private boolean isSelectedEnchantSpell(String source)
	{
		Widget selected = client.getSelectedWidget();
		return isEnchantSpellLabel(source,
			selected == null ? null : selected.getName(),
			selected == null ? null : selected.getText());
	}

	static boolean isEnchantSpellLabel(String... labels)
	{
		if (labels == null)
		{
			return false;
		}
		for (String label : labels)
		{
			if (clean(label).toLowerCase(Locale.ROOT).contains("enchant"))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isGrandExchangeOpen()
	{
		return client.getWidget(InterfaceID.GE_OFFERS, 0) != null;
	}

	private void logSailingInterface(int group, Widget widget, String option, String target,
		String itemName)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		log.debug("sailing interface group={} component={} child={} option='{}' target='{}' "
				+ "itemId={} itemName='{}' text='{}' name='{}'",
			group, widget == null ? -1 : widget.getId(), widget == null ? -1 : widget.getIndex(),
			option, clean(target), widget == null ? -1 : widget.getItemId(),
			itemName == null ? "(no item id)" : itemName,
			widget == null || widget.getText() == null ? "" : clean(widget.getText()),
			widget == null || widget.getName() == null ? "" : clean(widget.getName()));
	}

	private void logInterfaceProduct(MenuOptionClicked event, String product)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		Widget widget = event.getWidget();
		int itemId = widget == null ? -1 : widget.getItemId();
		String itemName = itemId > 0 ? itemName(itemId) : "(no item id)";
		log.debug("interface product raw='{}' stripped='{}' widgetItemId={} itemName='{}'",
			clean(event.getMenuTarget()), product, itemId, itemName);
	}

	public static boolean shouldHideWorldObjectCategory(String category)
	{
		return "woodcutting".equals(category);
	}

	public static boolean isInventoryMenuVisibilityExempt(MenuAction type, int menuGroup)
	{
		return menuGroup == InterfaceID.INVENTORY
			&& (type == MenuAction.WIDGET_TARGET || type == MenuAction.CC_OP
				|| type == MenuAction.CC_OP_LOW_PRIORITY);
	}

	public static boolean isShopBuyOption(int group, String option)
	{
		return group == InterfaceID.SHOPMAIN && option != null
			&& option.toLowerCase(Locale.ROOT).startsWith("buy");
	}

	public static boolean isShopSellOption(int group, String option)
	{
		return group == InterfaceID.SHOPSIDE && option != null
			&& option.toLowerCase(Locale.ROOT).startsWith("sell");
	}

	static boolean requiresInventoryCard(LockState itemUsageMode,
		FoodSettingsMode foodMode, String option)
	{
		if (!isInventoryUsageRestricted(itemUsageMode, option))
		{
			return false;
		}
		String optionLower = clean(option).toLowerCase(Locale.ROOT);
		if ("eat".equals(optionLower))
		{
			return foodMode == null || !foodMode.allowsEatAction();
		}
		if ("drink".equals(optionLower))
		{
			return foodMode == null || !foodMode.allowsDrinkAction();
		}
		return true;
	}

	public static boolean isLockedItemDisposalOption(String option)
	{
		return option != null && FORCED_DROP_ALLOWED.contains(option.toLowerCase(Locale.ROOT));
	}

	public static String stripProductQuantity(String product)
	{
		return product == null ? "" : product.trim()
			.replaceAll("(?i)\\s*x\\s*\\d{1,5}$", "")
			.replaceAll("(?i)^\\d{1,5}\\s*(x\\s+|\\s)", "")
			.trim();
	}

	public static boolean isMakeVerb(String optionLower)
	{
		if (optionLower == null)
		{
			return false;
		}
		for (String verb : MAKE_VERBS)
		{
			if (optionLower.startsWith(verb))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isGroundInteractionRestricted(LockState mode, MenuAction action,
		String option)
	{
		return mode == LockState.LOCKED
			&& (action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
				|| TAKE_OPTION.equals(clean(option).toLowerCase(Locale.ROOT)));
	}

	public static boolean isBankInteractionRestricted(LockState itemUsageMode,
		BankingMode bankingMode, String optionLower)
	{
		if (itemUsageMode != LockState.LOCKED || optionLower == null)
		{
			return false;
		}
		String cleanOption = optionLower.toLowerCase(Locale.ROOT);
		return cleanOption.startsWith("withdraw") && bankingMode != BankingMode.FULL
			|| cleanOption.startsWith("deposit") && bankingMode == BankingMode.OFF;
	}

	public static boolean isInventoryUsageRestricted(LockState itemUsageMode, String option)
	{
		return itemUsageMode == LockState.LOCKED && !isLockedItemDisposalOption(option);
	}

	private static boolean hasMissing(List<String> missing)
	{
		return missing != null && !missing.isEmpty();
	}

	private static String clean(String value)
	{
		return value == null ? "" : Text.removeTags(value).trim();
	}

	public static final class InteractionDecision
	{
		private static final InteractionDecision ALLOWED =
			new InteractionDecision(null, Collections.emptyList());

		private final String blockedItemName;
		private final List<String> missingCards;

		private InteractionDecision(String blockedItemName, List<String> missingCards)
		{
			this.blockedItemName = blockedItemName;
			this.missingCards = missingCards;
		}

		private static InteractionDecision allowed()
		{
			return ALLOWED;
		}

		private static InteractionDecision blockedItem(String itemName)
		{
			return itemName == null || itemName.isEmpty() ? ALLOWED
				: new InteractionDecision(itemName, Collections.emptyList());
		}

		private static InteractionDecision blockedCards(List<String> missingCards)
		{
			return hasMissing(missingCards)
				? new InteractionDecision(null, List.copyOf(missingCards)) : ALLOWED;
		}

		public boolean isBlocked()
		{
			return blockedItemName != null || !missingCards.isEmpty();
		}

		public String getBlockedItemName()
		{
			return blockedItemName;
		}

		public List<String> getMissingCards()
		{
			return missingCards;
		}
	}
}
