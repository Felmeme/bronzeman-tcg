package com.bronzemantcg;

import static com.bronzemantcg.PanelComponents.listDivider;
import static com.bronzemantcg.PanelComponents.makeClickable;
import static com.bronzemantcg.PanelComponents.mutedRow;
import static com.bronzemantcg.PanelComponents.row;
import static com.bronzemantcg.PanelComponents.sectionBody;
import static com.bronzemantcg.PanelComponents.styleHierarchyRow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Range;
import net.runelite.client.ui.ColorScheme;

/** Reflection-backed compact settings view shown inside the plugin panel. */
@Slf4j
final class SidePanelSettings
{
	private final JPanel panel = sectionBody();
	private final BronzemanTcgConfig config;
	private final ConfigManager configManager;
	private final List<Category> categories;
	private final Set<String> expandedCategories = new HashSet<>();
	private final Set<String> expandedSections = new HashSet<>();

	SidePanelSettings(BronzemanTcgConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
		this.categories = buildCategories();
	}

	JPanel component()
	{
		return panel;
	}

	void refresh()
	{
		panel.removeAll();
		for (int categoryIndex = 0; categoryIndex < categories.size(); categoryIndex++)
		{
			Category category = categories.get(categoryIndex);
			if (categoryIndex > 0)
			{
				panel.add(Box.createVerticalStrut(5));
			}
			boolean categoryExpanded = expandedCategories.contains(category.name);
			JPanel categoryRow = hierarchyLabelRow(category.name, categoryExpanded, false);
			makeClickable(categoryRow, () ->
			{
				toggle(expandedCategories, category.name);
				refresh();
			});
			panel.add(categoryRow);
			if (!categoryExpanded)
			{
				continue;
			}

			for (Section section : category.sections)
			{
				panel.add(Box.createVerticalStrut(3));
				boolean sectionExpanded = expandedSections.contains(section.key);
				JPanel sectionRow = hierarchyLabelRow(section.name, sectionExpanded, true);
				makeClickable(sectionRow, () ->
				{
					toggle(expandedSections, section.key);
					refresh();
				});
				panel.add(sectionRow);
				if (sectionExpanded)
				{
					panel.add(listDivider());
					for (Method method : section.items)
					{
						panel.add(settingControl(method));
					}
				}
			}
		}
		panel.revalidate();
		panel.repaint();
	}

	private static List<Category> buildCategories()
	{
		Map<String, List<Method>> bySection = new HashMap<>();
		for (Method method : BronzemanTcgConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && !item.hidden())
			{
				bySection.computeIfAbsent(item.section(), ignored -> new ArrayList<>())
					.add(method);
			}
		}
		for (List<Method> methods : bySection.values())
		{
			methods.sort(Comparator.comparingInt(
				method -> method.getAnnotation(ConfigItem.class).position()));
		}

		List<Category> result = new ArrayList<>();
		result.add(category("Gathering", bySection,
			section("Farming", BronzemanTcgConfig.farmingSection),
			section("Fishing", BronzemanTcgConfig.fishingSection),
			section("Hunter", BronzemanTcgConfig.hunterSection),
			section("Mining", BronzemanTcgConfig.miningSection),
			section("Thieving", BronzemanTcgConfig.thievingSection),
			section("Woodcutting", BronzemanTcgConfig.woodcuttingSection)));
		result.add(category("Production", bySection,
			section("Cooking", BronzemanTcgConfig.cookingSection),
			section("Crafting", BronzemanTcgConfig.craftingSection),
			section("Firemaking", BronzemanTcgConfig.firemakingSection),
			section("Fletching", BronzemanTcgConfig.fletchingSection),
			section("Herblore", BronzemanTcgConfig.herbloreSection),
			section("Runecrafting", BronzemanTcgConfig.runecraftingSection),
			section("Smithing", BronzemanTcgConfig.smithingSection)));
		result.add(category("Other", bySection,
			section("General", ""),
			section("Sailing", BronzemanTcgConfig.sailingSection),
			section("Slayer", BronzemanTcgConfig.slayerSection),
			section("Visuals", BronzemanTcgConfig.visualsSection),
			section("External Plugins", BronzemanTcgConfig.externalPluginsSection)));
		return result;
	}

	private static Category category(String name, Map<String, List<Method>> bySection,
		Section... definitions)
	{
		List<Section> sections = new ArrayList<>();
		for (Section definition : definitions)
		{
			List<Method> methods =
				bySection.getOrDefault(definition.key, Collections.emptyList());
			if (!methods.isEmpty())
			{
				sections.add(new Section(definition.name, definition.key, methods));
			}
		}
		return new Category(name, sections);
	}

	private static Section section(String name, String key)
	{
		return new Section(name, key, Collections.emptyList());
	}

	private JPanel settingControl(Method method)
	{
		ConfigItem item = method.getAnnotation(ConfigItem.class);
		Object value;
		try
		{
			value = method.invoke(config);
		}
		catch (ReflectiveOperationException ex)
		{
			log.warn("Could not read side-panel setting {}", item.keyName(), ex);
			JPanel unavailable = settingRow();
			unavailable.add(mutedRow(item.name() + " (unavailable)"));
			return unavailable;
		}

		String tooltip = item.description().replace("<br>", " ");
		if (value instanceof Boolean)
		{
			JCheckBox checkBox = new JCheckBox(item.name(), (Boolean) value);
			styleSettingComponent(checkBox, tooltip);
			checkBox.addActionListener(event ->
				save(item.keyName(), checkBox.isSelected()));
			JPanel result = settingRow();
			result.add(checkBox);
			return result;
		}

		JPanel result = settingRow();
		JLabel label = new JLabel(item.name());
		label.setForeground(Color.WHITE);
		label.setToolTipText(tooltip);
		result.add(label);
		result.add(Box.createVerticalStrut(3));

		if (value instanceof Enum)
		{
			JComboBox<Object> combo = new JComboBox<>(value.getClass().getEnumConstants());
			combo.setSelectedItem(value);
			styleSettingComponent(combo, tooltip);
			combo.addActionListener(event ->
				save(item.keyName(), ((Enum<?>) combo.getSelectedItem()).name()));
			result.add(combo);
		}
		else if (value instanceof Integer)
		{
			Range range = method.getAnnotation(Range.class);
			int min = range == null ? Integer.MIN_VALUE : range.min();
			int max = range == null ? Integer.MAX_VALUE : range.max();
			JSpinner spinner = new JSpinner(
				new SpinnerNumberModel(((Integer) value).intValue(), min, max, 1));
			styleSettingComponent(spinner, tooltip);
			spinner.addChangeListener(event -> save(item.keyName(), spinner.getValue()));
			result.add(spinner);
		}
		else if (value instanceof Color)
		{
			JButton colour = new JButton("Choose colour");
			colour.setBackground((Color) value);
			styleSettingComponent(colour, tooltip);
			colour.addActionListener(event ->
			{
				Color selected = JColorChooser.showDialog(panel, item.name(),
					colour.getBackground());
				if (selected != null)
				{
					colour.setBackground(selected);
					save(item.keyName(), selected);
				}
			});
			result.add(colour);
		}
		else
		{
			JTextField text = new JTextField(value == null ? "" : value.toString());
			styleSettingComponent(text, tooltip);
			Runnable saveText = () -> save(item.keyName(), text.getText());
			text.addActionListener(event -> saveText.run());
			text.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent event)
				{
					saveText.run();
				}
			});
			result.add(text);
		}
		return result;
	}

	private static JPanel hierarchyLabelRow(String label, boolean expanded, boolean nested)
	{
		JPanel result = row(new BorderLayout());
		styleHierarchyRow(result, expanded, nested);
		JLabel name = new JLabel(label);
		name.setForeground(Color.WHITE);
		result.add(name, BorderLayout.CENTER);
		return result;
	}

	private static JPanel settingRow()
	{
		JPanel result = sectionBody();
		result.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		result.setBorder(BorderFactory.createEmptyBorder(5, 18, 5, 4));
		return result;
	}

	private static void styleSettingComponent(Component component, String tooltip)
	{
		if (component instanceof javax.swing.JComponent)
		{
			javax.swing.JComponent swing = (javax.swing.JComponent) component;
			swing.setToolTipText(tooltip);
			swing.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			swing.setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		if (component instanceof JCheckBox)
		{
			((JCheckBox) component).setOpaque(false);
			((JCheckBox) component).setForeground(Color.WHITE);
		}
	}

	private void save(String key, Object value)
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP, key, value);
	}

	private static void toggle(Set<String> expanded, String key)
	{
		if (!expanded.remove(key))
		{
			expanded.add(key);
		}
	}

	private static final class Category
	{
		private final String name;
		private final List<Section> sections;

		private Category(String name, List<Section> sections)
		{
			this.name = name;
			this.sections = sections;
		}
	}

	private static final class Section
	{
		private final String name;
		private final String key;
		private final List<Method> items;

		private Section(String name, String key, List<Method> items)
		{
			this.name = name;
			this.key = key;
			this.items = items;
		}
	}
}
