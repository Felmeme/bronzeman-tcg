package com.bronzemantcg.restriction;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.interop.TcgCollectionReader;
import com.bronzemantcg.ownership.CardEntityKind;
import com.bronzemantcg.ownership.CardOwnershipService;
import com.bronzemantcg.ownership.ActiveCardIdentityCatalog;
import com.bronzemantcg.ownership.SharedUnlockStore;
import com.bronzemantcg.ownership.TcgOwnershipSnapshot;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Assembles the current restriction context and answers all base item/NPC/card lock questions.
 * Activity-specific policy remains with its route; this service owns only shared ownership,
 * exemptions, fail-open identity decisions and the global stand-down state.
 */
@Singleton
public final class RestrictionDecisionService
{
	private static final Set<Integer> LMS_REGIONS = Set.of(
		13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920,
		14174, 14175, 14176, 14430, 14431, 14432);

	private final Sources sources;
	private final CardOwnershipService ownershipService;
	private final LongSupplier catalogRevision;
	private Set<String> effectiveExemptions = Collections.emptySet();
	private Set<String> effectiveExemptionsBase;
	private boolean effectiveExemptionsCoins;
	private ItemContext itemContext;
	private ItemContext cachedItemContext;
	private final Map<Integer, Boolean> lockedItemCache = new HashMap<>();

	@Inject
	public RestrictionDecisionService(Client client, BronzemanTcgConfig config,
		TcgCollectionReader collectionReader, CardOwnershipService ownershipService,
		SharedUnlockStore sharedUnlockStore, ExemptionList exemptionList,
		ItemManager itemManager,
		ActiveCardIdentityCatalog activeCatalog)
	{
		this(new RuneLiteSources(client, config, collectionReader, sharedUnlockStore,
			exemptionList, itemManager), ownershipService,
			activeCatalog::getRevision);
	}

	RestrictionDecisionService(Sources sources, CardOwnershipService ownershipService,
		LongSupplier catalogRevision)
	{
		this.sources = sources;
		this.ownershipService = ownershipService;
		this.catalogRevision = catalogRevision;
	}

	/** One stand-down check shared by enforcement and the corresponding visual state. */
	public boolean isEnforcementBypassed()
	{
		return isTutorialIslandProgress(sources.getTutorialProgress())
			|| isLmsBypassed()
			|| !sources.isOwnershipStateAvailable();
	}

	private boolean isLmsBypassed()
	{
		int lmsState = sources.getLmsState();
		return lmsState == 1 || isLmsBypassed(lmsState, sources.getMapRegions());
	}

	public static boolean isTutorialIslandProgress(int progress)
	{
		return progress > 0 && progress < 1000;
	}

	static boolean isLmsBypassed(int lmsState, int[] mapRegions)
	{
		if (lmsState == 1)
		{
			return true;
		}
		if (mapRegions == null)
		{
			return false;
		}
		for (int region : mapRegions)
		{
			if (LMS_REGIONS.contains(region))
			{
				return true;
			}
		}
		return false;
	}

	/** Context shared by all item and name-authored requirement decisions in one evaluation. */
	public ItemContext getItemContext()
	{
		long revision;
		TcgOwnershipSnapshot ownership;
		Set<String> shared;
		Set<String> exempt;
		do
		{
			revision = catalogRevision.getAsLong();
			ownership = sources.getOwnershipSnapshot();
			shared = sharedCardNames();
			exempt = effectiveRequirementExemptCardNames();
		}
		while (revision != catalogRevision.getAsLong());
		ItemContext current = itemContext;
		if (current == null || current.ownership != ownership || current.shared != shared
			|| current.exempt != exempt || current.catalogRevision != revision)
		{
			current = new ItemContext(ownership, shared, exempt, revision);
			itemContext = current;
		}
		return current;
	}

	public boolean isItemLocked(int itemId, String itemName)
	{
		return isItemLocked(itemId, itemName, getItemContext());
	}

	public boolean isItemLocked(int itemId, String itemName, ItemContext context)
	{
		return missingItemCardName(itemId, itemName, context) != null;
	}

	/** Returns the canonical v1 parent name when an item is locked, otherwise null. */
	public String missingItemCardName(int itemId, String itemName)
	{
		return missingItemCardName(itemId, itemName, getItemContext());
	}

	private String missingItemCardName(int itemId, String itemName, ItemContext context)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return null;
		}
		CardOwnershipService.Decision decision = ownershipService.decide(
			CardEntityKind.ITEM, itemId, itemName,
			context.ownership, context.shared, context.exempt);
		return !decision.isAllowed() && decision.getIdentity() != null
			? decision.getIdentity().getCardName() : null;
	}

	/** Cached item-ID path used by widget fading and the item-icon overlay. */
	public boolean isItemLocked(int itemId)
	{
		ItemContext context = getItemContext();
		if (cachedItemContext != context)
		{
			lockedItemCache.clear();
			cachedItemContext = context;
		}
		Boolean cached = lockedItemCache.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		boolean locked = isItemLocked(itemId, sources.getItemName(itemId), context);
		lockedItemCache.put(itemId, locked);
		return locked;
	}

	public boolean isNpcLocked(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}
		String name = resolveNpcName(npc);
		return name != null && !name.isEmpty() && isNpcLocked(npc.getId(), name);
	}

	public boolean isNpcLocked(int npcId, String name)
	{
		if (name == null || name.isEmpty()
			|| NpcRestrictionPolicy.isCardRestrictionExempt(npcId, name))
		{
			return false;
		}
		return !ownershipService.decide(CardEntityKind.NPC, npcId, name,
			sources.getOwnershipSnapshot(), sharedCardNames(),
			sources.getConfiguredExemptCardNames()).isAllowed();
	}

	/** Stable ownership predicate for one complete node or recipe evaluation. */
	public Predicate<String> requirementOwnership()
	{
		ItemContext context = getItemContext();
		return cardName -> ownershipService.decideCard(cardName,
			context.ownership, context.shared, context.exempt).isAllowed();
	}

	/** Stable ownership predicate for requirements authored in one entity namespace. */
	public Predicate<String> requirementOwnership(CardEntityKind kind)
	{
		ItemContext context = getItemContext();
		return cardName -> ownershipService.decideCard(kind, cardName,
			context.ownership, context.shared, context.exempt).isAllowed();
	}

	private Set<String> sharedCardNames()
	{
		return sources.acceptSharedUnlocks()
			? sources.getSharedCardNames() : Collections.emptySet();
	}

	private Set<String> effectiveRequirementExemptCardNames()
	{
		Set<String> configured = sources.getConfiguredExemptCardNames();
		boolean coins = sources.isCoinsExempt();
		if (configured.isEmpty() && !coins)
		{
			return configured;
		}
		if (configured != effectiveExemptionsBase || coins != effectiveExemptionsCoins)
		{
			Set<String> combined = new HashSet<>(configured);
			if (coins)
			{
				combined.add("coins");
			}
			effectiveExemptions = Collections.unmodifiableSet(combined);
			effectiveExemptionsBase = configured;
			effectiveExemptionsCoins = coins;
		}
		return effectiveExemptions;
	}

	public String resolveNpcName(NPC npc)
	{
		NPCComposition composition = npc.getTransformedComposition();
		String name = composition != null ? composition.getName() : npc.getName();
		return name == null ? null : Text.removeTags(name).trim();
	}

	public static final class ItemContext
	{
		private final TcgOwnershipSnapshot ownership;
		private final Set<String> shared;
		private final Set<String> exempt;
		private final long catalogRevision;

		private ItemContext(TcgOwnershipSnapshot ownership, Set<String> shared,
			Set<String> exempt, long catalogRevision)
		{
			this.ownership = ownership;
			this.shared = shared;
			this.exempt = exempt;
			this.catalogRevision = catalogRevision;
		}
	}

	interface Sources
	{
		TcgOwnershipSnapshot getOwnershipSnapshot();
		boolean isOwnershipStateAvailable();
		boolean acceptSharedUnlocks();
		Set<String> getSharedCardNames();
		Set<String> getConfiguredExemptCardNames();
		boolean isCoinsExempt();
		int getTutorialProgress();
		int getLmsState();
		int[] getMapRegions();
		String getItemName(int itemId);
	}

	private static final class RuneLiteSources implements Sources
	{
		private final Client client;
		private final BronzemanTcgConfig config;
		private final TcgCollectionReader collectionReader;
		private final SharedUnlockStore sharedUnlockStore;
		private final ExemptionList exemptionList;
		private final ItemManager itemManager;

		private RuneLiteSources(Client client, BronzemanTcgConfig config,
			TcgCollectionReader collectionReader, SharedUnlockStore sharedUnlockStore,
			ExemptionList exemptionList, ItemManager itemManager)
		{
			this.client = client;
			this.config = config;
			this.collectionReader = collectionReader;
			this.sharedUnlockStore = sharedUnlockStore;
			this.exemptionList = exemptionList;
			this.itemManager = itemManager;
		}

		@Override
		public TcgOwnershipSnapshot getOwnershipSnapshot()
		{
			return collectionReader.getOwnershipSnapshot();
		}

		@Override
		public boolean isOwnershipStateAvailable()
		{
			return collectionReader.isStateAvailable();
		}

		@Override
		public boolean acceptSharedUnlocks()
		{
			return config.acceptSharedUnlocks();
		}

		@Override
		public Set<String> getSharedCardNames()
		{
			return sharedUnlockStore.getSharedCardNamesLowerCase();
		}

		@Override
		public Set<String> getConfiguredExemptCardNames()
		{
			return exemptionList.resolve(config.lootExemptNames()).getCardNamesLowerCase();
		}

		@Override
		public boolean isCoinsExempt()
		{
			return config.coinMode() == LockState.UNLOCKED;
		}

		@Override
		public int getTutorialProgress()
		{
			return client.getVarpValue(VarPlayerID.TUTORIAL);
		}

		@Override
		public int getLmsState()
		{
			return client.getVarbitValue(VarbitID.BR_INGAME);
		}

		@Override
		public int[] getMapRegions()
		{
			WorldView worldView = client.getTopLevelWorldView();
			return worldView == null ? null : worldView.getMapRegions();
		}

		@Override
		public String getItemName(int itemId)
		{
			return itemManager.getItemComposition(itemId).getName();
		}
	}
}
