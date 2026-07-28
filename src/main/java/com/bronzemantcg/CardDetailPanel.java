package com.bronzemantcg;

import static com.bronzemantcg.PanelComponents.hierarchyProgressRow;
import static com.bronzemantcg.PanelComponents.makeClickable;
import static com.bronzemantcg.PanelComponents.mutedRow;
import static com.bronzemantcg.PanelComponents.row;
import static com.bronzemantcg.PanelComponents.sectionBody;
import static com.bronzemantcg.PanelComponents.sectionHeader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/** Renders card knowledge without owning the surrounding tab/navigation state. */
final class CardDetailPanel
{
	private final JPanel panel = sectionBody();
	private final ItemManager itemManager;
	private final QuestCatalog questCatalog;
	private final Function<String, String> displayCardName;
	private final Consumer<String> openCard;
	private final Set<String> expandedSections = new HashSet<>();
	private Consumer<String> rerender;

	CardDetailPanel(ItemManager itemManager, QuestCatalog questCatalog,
		Function<String, String> displayCardName, Consumer<String> openCard)
	{
		this.itemManager = itemManager;
		this.questCatalog = questCatalog;
		this.displayCardName = displayCardName;
		this.openCard = openCard;
	}

	JPanel component()
	{
		return panel;
	}

	void render(CardKnowledgeCatalog.Card card, Set<String> owned, JButton backButton,
		Consumer<String> rerender)
	{
		this.rerender = rerender;
		panel.removeAll();
		panel.add(backButton);
		if (card == null)
		{
			panel.add(mutedRow("No card information is available."));
			finish();
			return;
		}

		panel.add(Box.createVerticalStrut(8));
		panel.add(cardHeader(card));
		panel.add(Box.createVerticalStrut(8));
		addDetailField(panel, "Status",
			owned.contains(card.name.toLowerCase(Locale.ROOT)) ? "Unlocked" : "Locked");
		addDetailField(panel, "Type", card.isResource() ? "Resource" : "Monster");
		if (!card.categoryLabels().isEmpty())
		{
			addDetailField(panel, "Categories", String.join(", ", card.categoryLabels()));
		}
		if (card.combatLevel != null && card.combatLevel > 0)
		{
			addDetailField(panel, "Combat level", String.valueOf(card.combatLevel));
		}
		if (card.slayerLevel != null && card.slayerLevel > 0)
		{
			addDetailField(panel, "Slayer level", String.valueOf(card.slayerLevel));
		}
		if (card.examine != null && !card.examine.isEmpty())
		{
			panel.add(sectionHeader("Examine"));
			panel.add(wrappedDetailText(card.examine));
		}
		if (card.isResource())
		{
			addResourceDetails(card);
		}
		else
		{
			addMonsterDetails(card);
		}
		addQuestRelationships(card);
		finish();
	}

	private void finish()
	{
		panel.revalidate();
		panel.repaint();
	}

	private JPanel cardHeader(CardKnowledgeCatalog.Card card)
	{
		JPanel header = row(new BorderLayout(8, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(153, 102, 51)),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));

		if (card.isResource() && card.primaryId() >= 0)
		{
			JLabel image = new JLabel();
			image.setPreferredSize(new Dimension(36, 36));
			AsyncBufferedImage sprite = itemManager.getImage(card.primaryId());
			sprite.addTo(image);
			header.add(image, BorderLayout.WEST);
		}

		JLabel title = new JLabel("<html>" + escapeHtml(card.name) + "</html>");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		header.add(title, BorderLayout.CENTER);
		return header;
	}

	private void addResourceDetails(CardKnowledgeCatalog.Card card)
	{
		CardKnowledgeCatalog.Sources sources = card.sources;
		if (sources == null)
		{
			panel.add(mutedRow("No source information available."));
			return;
		}

		addKnowledgeSection(card.name, "Used in", safeSize(sources.usedIn), body ->
		{
			for (CardKnowledgeCatalog.UsedIn usage : sources.usedIn)
			{
				body.add(linkedKnowledgeRow(usage.card, "x" + formatNumber(usage.quantity)));
			}
		});

		List<CardKnowledgeCatalog.MonsterSource> monsters = new ArrayList<>();
		if (sources.monsters != null)
		{
			monsters.addAll(sources.monsters);
		}
		if (sources.rareDropTable != null)
		{
			monsters.addAll(sources.rareDropTable);
		}
		addKnowledgeSection(card.name, "Monster drops", monsters.size(), body ->
		{
			for (CardKnowledgeCatalog.MonsterSource source : monsters)
			{
				String detail = source.fraction == null || source.fraction.isEmpty()
					? source.rarity : source.rarity + " \u00b7 " + source.fraction;
				if (!source.confirmedByMonsterDrops)
				{
					detail += " \u00b7 one-sided";
				}
				body.add(linkedKnowledgeRow(source.card, detail));
			}
		});

		if (sources.production != null)
		{
			int hidden = 0;
			for (CardKnowledgeCatalog.Ingredient ingredient : sources.production.ingredients)
			{
				if (!ingredient.hasCard)
				{
					hidden++;
				}
			}
			final int hiddenCount = hidden;
			addKnowledgeSection(card.name, "Production",
				sources.production.ingredients.size(), body ->
			{
				CardKnowledgeCatalog.Production production = sources.production;
				addDetailField(body, "Skill", levelLabel(production.skill, production.level));
				if (production.facilities != null)
				{
					addDetailField(body, "Facility", production.facilities);
				}
				if (production.tools != null && !production.tools.isEmpty())
				{
					addDetailField(body, "Tools", String.join(", ", production.tools));
				}
				for (CardKnowledgeCatalog.Ingredient ingredient : production.ingredients)
				{
					if (ingredient.hasCard)
					{
						body.add(linkedKnowledgeRow(ingredient.item,
							"x" + formatNumber(ingredient.quantity)));
					}
				}
				if (hiddenCount > 0)
				{
					body.add(mutedRow(hiddenCount + " non-card ingredient"
						+ (hiddenCount == 1 ? "" : "s") + " hidden"));
				}
			});
		}

		if (sources.gathering != null)
		{
			addKnowledgeSection(card.name, "Gathering", 1, body ->
			{
				CardKnowledgeCatalog.Gathering gathering = sources.gathering;
				addDetailField(body, "Method", valueOrDash(gathering.method));
				addDetailField(body, "Skill", levelLabel(gathering.skill, gathering.level));
				addDetailField(body, "Tool", valueOrDash(gathering.tool));
				if (gathering.xp != null)
				{
					addDetailField(body, "XP", formatNumber(gathering.xp));
				}
			});
		}

		addTextSection(card.name, "Ground spawns", sources.spawns);
		addKnowledgeSection(card.name, "Shops", safeSize(sources.shops), body ->
		{
			for (CardKnowledgeCatalog.Shop shop : sources.shops)
			{
				String price = shop.price == null ? "" : formatNumber(shop.price)
					+ (shop.currency == null ? "" : " " + shop.currency);
				body.add(plainKnowledgeRow(shop.seller, price));
			}
		});
		addTextSection(card.name, "Clue rewards", sources.clueTiers);
	}

	private void addMonsterDetails(CardKnowledgeCatalog.Card card)
	{
		List<CardKnowledgeCatalog.Drop> drops =
			card.drops == null ? Collections.emptyList() : card.drops;
		if (drops.isEmpty())
		{
			panel.add(mutedRow("No card drops available."));
			return;
		}
		addKnowledgeSection(card.name, "Drops", drops.size(), body ->
		{
			for (CardKnowledgeCatalog.Drop drop : drops)
			{
				List<String> details = new ArrayList<>();
				if (drop.rarity != null && !drop.rarity.isEmpty())
				{
					details.add(drop.rarity);
				}
				if (drop.fraction != null && !drop.fraction.isEmpty())
				{
					details.add(drop.fraction);
				}
				if (drop.fromRdt)
				{
					details.add("RDT");
				}
				body.add(linkedKnowledgeRow(drop.card, String.join(" \u00b7 ", details)));
			}
		});
	}

	private void addQuestRelationships(CardKnowledgeCatalog.Card card)
	{
		List<String> quests = questCatalog.getQuestsForCard(card.name);
		addKnowledgeSection(card.name, "Quests", quests.size(), body ->
		{
			for (String quest : quests)
			{
				body.add(plainKnowledgeRow(quest, ""));
			}
		});
	}

	private void addTextSection(String cardName, String title, List<String> values)
	{
		addKnowledgeSection(cardName, title, safeSize(values), body ->
		{
			for (String value : values)
			{
				body.add(plainKnowledgeRow(value, ""));
			}
		});
	}

	private void addKnowledgeSection(String cardName, String title, int count,
		Consumer<JPanel> bodyBuilder)
	{
		if (count == 0)
		{
			return;
		}
		String key = cardName + "\0" + title;
		boolean expanded = expandedSections.contains(key);
		JPanel heading = hierarchyProgressRow(title, count, count, true, expanded, false);
		makeClickable(heading, () ->
		{
			if (!expandedSections.remove(key))
			{
				expandedSections.add(key);
			}
			rerender.accept(cardName);
		});
		panel.add(Box.createVerticalStrut(5));
		panel.add(heading);
		if (expanded)
		{
			JPanel body = sectionBody();
			body.setBorder(BorderFactory.createEmptyBorder(3, 5, 2, 0));
			bodyBuilder.accept(body);
			panel.add(body);
		}
	}

	private JPanel linkedKnowledgeRow(String cardName, String detail)
	{
		JPanel item = plainKnowledgeRow(displayCardName.apply(cardName), detail);
		makeClickable(item, () -> openCard.accept(cardName));
		item.setToolTipText("View " + displayCardName.apply(cardName));
		return item;
	}

	private static JPanel plainKnowledgeRow(String label, String detail)
	{
		JPanel item = row(new BorderLayout(5, 0));
		item.setBackground(Color.BLACK);
		item.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		item.add(name, BorderLayout.CENTER);
		if (detail != null && !detail.isEmpty())
		{
			JLabel value = new JLabel(detail);
			value.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			value.setFont(value.getFont().deriveFont(10f));
			item.add(value, BorderLayout.EAST);
		}
		return item;
	}

	private static void addDetailField(JPanel target, String label, String value)
	{
		JPanel field = row(new BorderLayout(6, 0));
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JLabel name = new JLabel(label);
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		field.add(name, BorderLayout.WEST);
		JLabel detail = new JLabel(value);
		detail.setForeground(Color.WHITE);
		field.add(detail, BorderLayout.EAST);
		target.add(field);
		target.add(Box.createVerticalStrut(2));
	}

	private static JLabel wrappedDetailText(String text)
	{
		JLabel label = new JLabel("<html><body style='width:175px'>"
			+ escapeHtml(text) + "</body></html>");
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 6, 6));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static String levelLabel(String skill, Integer level)
	{
		String name = valueOrDash(skill);
		return level == null || level <= 0 ? name : name + " " + level;
	}

	private static String valueOrDash(String value)
	{
		return value == null || value.trim().isEmpty() ? "\u2014" : value;
	}

	private static String formatNumber(Number number)
	{
		if (number == null)
		{
			return "";
		}
		double value = number.doubleValue();
		return value == Math.rint(value)
			? String.valueOf(number.longValue()) : String.valueOf(value);
	}

	private static int safeSize(List<?> values)
	{
		return values == null ? 0 : values.size();
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;")
			.replace(">", "&gt;").replace("\"", "&quot;");
	}
}
