package com.bronzemantcg.feature;

import com.bronzemantcg.BronzemanTcgConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;


@Slf4j
@Singleton
public final class DuelistCityController
{
	private static final int MYSTIC_CARDS_ITEM_ID = ItemID.LEAGUE_3_CARDTHROW;

	private static final int[] MYSTIC_STANCE =
			{9847, 9849, 9850, 823, 823, 9851, 9852, 820};

	private final Client client;
	private final BronzemanTcgConfig config;
	private final Map<Integer, int[]> realEquipmentByPlayer = new HashMap<>();

	private int[] lastLoggedStance;

	@Inject
	public DuelistCityController(Client client, BronzemanTcgConfig config)
	{
		this.client = client;
		this.config = config;
	}

	private static void applyStance(Player player, int[] stance)
	{
		player.setIdlePoseAnimation(stance[0]);
		player.setWalkAnimation(stance[1]);
		player.setRunAnimation(stance[2]);
		player.setIdleRotateLeft(stance[3]);
		player.setIdleRotateRight(stance[4]);
		player.setWalkRotateLeft(stance[5]);
		player.setWalkRotateRight(stance[6]);
		player.setWalkRotate180(stance[7]);
	}

	private void applyDuelistCards(Player player)
	{
		if (player == null)
		{
			return;
		}

		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return;
		}
		int[] equipmentIds = composition.getEquipmentIds();

		int weaponIndex = KitType.WEAPON.getIndex();
		int shieldIndex = KitType.SHIELD.getIndex();

		int fakeWeapon = MYSTIC_CARDS_ITEM_ID + PlayerComposition.ITEM_OFFSET;

		if (equipmentIds[weaponIndex] != fakeWeapon)
		{
			int[] realAppearance =
					{
							equipmentIds[weaponIndex],
							equipmentIds[shieldIndex],
							player.getIdlePoseAnimation(),
							player.getWalkAnimation(),
							player.getRunAnimation(),
							player.getIdleRotateLeft(),
							player.getIdleRotateRight(),
							player.getWalkRotateLeft(),
							player.getWalkRotateRight(),
							player.getWalkRotate180()
					};

			realEquipmentByPlayer.put(player.getId(), realAppearance);
		}
		equipmentIds[weaponIndex] = fakeWeapon;
		equipmentIds[shieldIndex] = 0;

		composition.setHash();
		applyStance(player, MYSTIC_STANCE);
	}

	private void restoreDuelistCards(Player player)
	{
		if (player == null)
		{
			return;
		}

		PlayerComposition composition = player.getPlayerComposition();

		int[] realAppearance =
				realEquipmentByPlayer.remove(player.getId());

		if (composition == null || realAppearance == null)
		{
			return;
		}
		int[] equipmentIds = composition.getEquipmentIds();

		equipmentIds[KitType.WEAPON.getIndex()] = realAppearance[0];
		equipmentIds[KitType.SHIELD.getIndex()] = realAppearance[1];

		composition.setHash();
		int[] realStance =
				Arrays.copyOfRange(realAppearance, 2, realAppearance.length);

		applyStance(player, realStance);
	}

	public void onPlayerSpawned(Player player)
	{
		if (config.duelistCityMode())
		{
			applyDuelistCards(player);
		}
	}

	public void onPlayerChanged(Player player)
	{
		if (config.duelistCityMode())
		{
			applyDuelistCards(player);
		}
	}

	public void onPlayerDespawned(Player player)
	{
		if (player == null)
		{
			return;
		}

		realEquipmentByPlayer.remove(player.getId());
	}

	public void setEnabled(boolean enabled)
	{
		WorldView worldView = client.getTopLevelWorldView();

		if (worldView == null)
		{
			return;
		}
		for (Player player : worldView.players())
		{
			if (enabled)
			{
				applyDuelistCards(player);
			} else
			{
				restoreDuelistCards(player);
			}
		}
	}

	public void shutDown()
	{
		setEnabled(false);
		realEquipmentByPlayer.clear();
	}

	private void logLocalStanceOnChange()
	{
		if (!log.isDebugEnabled())
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}
		int[] currentStance =
				{
						localPlayer.getIdlePoseAnimation(),
						localPlayer.getWalkAnimation(),
						localPlayer.getRunAnimation(),
						localPlayer.getIdleRotateLeft(),
						localPlayer.getIdleRotateRight(),
						localPlayer.getWalkRotateLeft(),
						localPlayer.getWalkRotateRight(),
						localPlayer.getWalkRotate180()
				};

		if (Arrays.equals(currentStance, lastLoggedStance))
		{
			return;
		}

		lastLoggedStance = currentStance;
		log.debug(
				"local stance: idle={} walk={} run={} idleRotL={} idleRotR={} "
						+ "walkRotL={} walkRotR={} walkRot180={}",
				currentStance[0],
				currentStance[1],
				currentStance[2],
				currentStance[3],
				currentStance[4],
				currentStance[5],
				currentStance[6],
				currentStance[7]
		);
	}

	public void onGameTick()
	{
		logLocalStanceOnChange();
	}

}
