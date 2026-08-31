package com.bronzemantcg.settings;

import static com.bronzemantcg.panel.PanelComponents.listDivider;
import static com.bronzemantcg.panel.PanelComponents.makeClickable;
import static com.bronzemantcg.panel.PanelComponents.mutedRow;
import static com.bronzemantcg.panel.PanelComponents.row;
import static com.bronzemantcg.panel.PanelComponents.sectionBody;
import static com.bronzemantcg.panel.PanelComponents.styleHierarchyRow;

import com.bronzemantcg.BronzemanTcgConfig;
import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

/** Explicit-registry-backed compact settings view shown inside the plugin panel. */
@Slf4j
public final class SidePanelSettings
{
	private final JPanel panel = sectionBody();
	private final ConfigManager configManager;
	private final BronzemanSettingsManager settingsManager;
	private final Runnable closeSettings;
	private final List<Category> categories;
	private final Set<String> expandedCategories = new HashSet<>();
	private final Set<String> expandedSections = new HashSet<>();
	private boolean onboardingPending;
	private boolean rebuilding;

	public SidePanelSettings(Gson gson, BronzemanTcgConfig config, ConfigManager configManager,
		boolean onboardingPending, Runnable closeSettings)
	{
		this.configManager = configManager;
		this.settingsManager = new BronzemanSettingsManager(gson, config, configManager);
		this.onboardingPending = onboardingPending;
		this.closeSettings = closeSettings;
		this.categories = buildCategories();
	}

	public JPanel component()
	{
		return panel;
	}

	public void refresh()
	{
		rebuilding = true;
		try
		{
			rebuild();
		}
		finally
		{
			rebuilding = false;
		}
	}

	private void rebuild()
	{
		panel.removeAll();
		if (onboardingPending)
		{
			renderOnboarding();
			panel.revalidate();
			panel.repaint();
			return;
		}

		renderPresetTools();
		panel.add(Box.createVerticalStrut(8));
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
					for (SidePanelSettingMetadata.Entry item : section.items)
					{
						panel.add(settingControl(item));
					}
				}
			}
		}
		panel.revalidate();
		panel.repaint();
	}

	private void renderOnboarding()
	{
		JLabel title = new JLabel("Choose your playstyle");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(title);
		panel.add(Box.createVerticalStrut(5));
		panel.add(wrappedLabel("This only sets Bronzeman's gameplay restrictions. "
			+ "You can change or replace the preset later."));
		panel.add(Box.createVerticalStrut(10));
		panel.add(presetButton(BronzemanPreset.TCG_LOCKED, true));
		panel.add(Box.createVerticalStrut(5));
		panel.add(presetButton(BronzemanPreset.MAXIMUM, true));
		panel.add(Box.createVerticalStrut(5));

		JButton manual = fullWidthButton("Configure Manually");
		manual.setToolTipText("Keep the current settings and open the full settings list.");
		manual.addActionListener(event -> completeOnboarding(false));
		panel.add(manual);
	}

	private void renderPresetTools()
	{
		JPanel tools = sectionBody();
		tools.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(153, 102, 51)),
			BorderFactory.createEmptyBorder(7, 7, 7, 7)));
		JLabel title = new JLabel("Playstyle Presets");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
		tools.add(title);
		tools.add(Box.createVerticalStrut(5));
		tools.add(presetButton(BronzemanPreset.TCG_LOCKED, false));
		tools.add(Box.createVerticalStrut(4));
		tools.add(presetButton(BronzemanPreset.MAXIMUM, false));
		tools.add(Box.createVerticalStrut(7));

		JPanel sharing = row(new java.awt.GridLayout(1, 2, 4, 0));
		sharing.setOpaque(false);
		JButton export = new JButton("Export");
		export.setToolTipText("Copy gameplay settings. Personal exemptions are excluded.");
		export.addActionListener(event -> exportSettings());
		sharing.add(export);
		JButton importButton = new JButton("Import");
		importButton.setToolTipText("Preview and apply a shared Bronzeman settings string.");
		importButton.addActionListener(event -> importSettings());
		sharing.add(importButton);
		tools.add(sharing);
		panel.add(tools);
	}

	private JButton presetButton(BronzemanPreset preset, boolean firstRun)
	{
		JButton button = fullWidthButton(preset.getLabel());
		button.setToolTipText(preset == BronzemanPreset.TCG_LOCKED
			? "Input-only production, tool-only gathering, free pickup and full banking."
			: "Every supported restriction and extreme option enabled.");
		button.addActionListener(event -> applyPreset(preset, firstRun));
		return button;
	}

	private void applyPreset(BronzemanPreset preset, boolean firstRun)
	{
		List<BronzemanSettingsManager.Change> changes =
			settingsManager.changes(preset.getSettings());
		String effect = preset == BronzemanPreset.TCG_LOCKED
			? "Allows pickup and full banking, restricts item use, uses input-only "
				+ "production and tool-only gathering."
			: "Disables banking and enables all supported card requirements and "
				+ "extreme options.";
		int result = JOptionPane.showConfirmDialog(panel,
			effect + "\n\n" + changes.size() + " setting(s) will change.\n"
				+ "Visuals, party sharing and your exemption list are untouched.\n\n"
				+ "If RuneLite's settings page is already open, leave and reopen\n"
				+ "Bronzeman TCG there to refresh its displayed values.",
			"Apply " + preset.getLabel() + "?",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
		{
			return;
		}
		try
		{
			settingsManager.apply(preset);
		}
		catch (IllegalStateException ex)
		{
			log.warn("Could not apply preset {}", preset, ex);
			JOptionPane.showMessageDialog(panel, ex.getMessage(), "Preset Not Applied",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (firstRun)
		{
			completeOnboarding(true);
		}
		else
		{
			refresh();
		}
	}

	private void exportSettings()
	{
		String encoded = settingsManager.exportSettings();
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(encoded), null);
		}
		catch (RuntimeException ex)
		{
			log.debug("Could not copy settings to the clipboard", ex);
		}
		JTextArea text = textArea(encoded);
		JOptionPane.showMessageDialog(panel, new JScrollPane(text),
			"Bronzeman Settings (copied)", JOptionPane.INFORMATION_MESSAGE);
	}

	private void importSettings()
	{
		JTextArea text = textArea("");
		int entered = JOptionPane.showConfirmDialog(panel, new JScrollPane(text),
			"Paste Bronzeman Settings", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE);
		if (entered != JOptionPane.OK_OPTION)
		{
			return;
		}

		Map<String, String> imported;
		try
		{
			imported = settingsManager.importSettings(text.getText());
		}
		catch (IllegalArgumentException ex)
		{
			JOptionPane.showMessageDialog(panel, ex.getMessage(), "Import Failed",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		List<BronzemanSettingsManager.Change> changes = settingsManager.changes(imported);
		StringBuilder preview = new StringBuilder();
		preview.append(changes.size()).append(" setting(s) will change.\n")
			.append("\nIf RuneLite's settings page is already open, leave and reopen ")
			.append("Bronzeman TCG there to refresh its displayed values.\n");
		for (BronzemanSettingsManager.Change change : changes)
		{
			preview.append("\n").append(change.name).append(":\n  ")
				.append(change.oldValue).append(" -> ").append(change.newValue);
		}
		JTextArea previewText = textArea(preview.toString());
		previewText.setEditable(false);
		JScrollPane previewScroll = new JScrollPane(previewText);
		previewScroll.setPreferredSize(new Dimension(390, 260));
		int confirmed = JOptionPane.showConfirmDialog(panel, previewScroll,
			"Import Bronzeman Settings?", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (confirmed == JOptionPane.OK_OPTION)
		{
			try
			{
				settingsManager.apply(imported);
				refresh();
			}
			catch (IllegalStateException ex)
			{
				log.warn("Could not import settings", ex);
				JOptionPane.showMessageDialog(panel, ex.getMessage(), "Import Not Applied",
					JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void completeOnboarding(boolean close)
	{
		configManager.setConfiguration(BronzemanTcgConfig.GROUP,
			"presetOnboardingComplete", true);
		configManager.unsetConfiguration(BronzemanTcgConfig.GROUP,
			"presetOnboardingPending");
		onboardingPending = false;
		if (close)
		{
			closeSettings.run();
		}
		else
		{
			refresh();
		}
	}

	private static JButton fullWidthButton(String text)
	{
		JButton button = new JButton(text);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return button;
	}

	private static JTextArea textArea(String value)
	{
		JTextArea text = new JTextArea(value, 6, 26);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		return text;
	}

	private static JLabel wrappedLabel(String text)
	{
		JLabel label = new JLabel("<html><body style='width:190px'>" + text + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static List<Category> buildCategories()
	{
		List<Category> result = new ArrayList<>();
		for (SidePanelSettingMetadata.Category category
			: SidePanelSettingMetadata.Category.values())
		{
			List<Section> sections = new ArrayList<>();
			for (SidePanelSettingMetadata.Section section
				: SidePanelSettingMetadata.Section.values())
			{
				if (section.category != category)
				{
					continue;
				}
				List<SidePanelSettingMetadata.Entry> items = new ArrayList<>();
				for (SidePanelSettingMetadata.Entry entry : SidePanelSettingMetadata.all())
				{
					if (entry.section == section)
					{
						BronzemanSettingRegistry.require(entry.key);
						items.add(entry);
					}
				}
				if (!items.isEmpty())
				{
					sections.add(new Section(section.label, section.name(), items));
				}
			}
			result.add(new Category(category.label, sections));
		}
		return Collections.unmodifiableList(result);
	}

	private JPanel settingControl(SidePanelSettingMetadata.Entry item)
	{
		BronzemanSettingRegistry.Definition definition =
			BronzemanSettingRegistry.require(item.key);
		Object value;
		try
		{
			value = settingsManager.read(definition);
		}
		catch (RuntimeException ex)
		{
			log.warn("Could not read side-panel setting {}", item.key, ex);
			JPanel unavailable = settingRow();
			unavailable.add(mutedRow(item.name + " (unavailable)"));
			return unavailable;
		}

		String tooltip = "<html>" + item.name + ":<br>"
			+ item.description + "</html>";
		if (value instanceof Boolean)
		{
			JCheckBox checkBox = new JCheckBox(item.name, (Boolean) value);
			styleSettingComponent(checkBox, tooltip);
			checkBox.addActionListener(event ->
				save(item.key, checkBox.isSelected()));
			JPanel result = settingRow();
			result.add(checkBox);
			return result;
		}

		JPanel result = settingRow();
		JLabel label = new JLabel(item.name);
		label.setForeground(Color.WHITE);
		label.setToolTipText(tooltip);
		result.add(label);
		result.add(Box.createVerticalStrut(3));

		if (value instanceof Enum)
		{
			JComboBox<Object> combo = new JComboBox<>(
				definition.getEnumValues().toArray());
			combo.setSelectedItem(value);
			styleSettingComponent(combo, tooltip);
			combo.addActionListener(event ->
			{
				Object selected = combo.getSelectedItem();
				if (selected instanceof Enum)
				{
					save(item.key, ((Enum<?>) selected).name());
				}
			});
			result.add(combo);
		}
		else if (value instanceof Integer)
		{
			JSpinner spinner = new JSpinner(
				new SpinnerNumberModel(((Integer) value).intValue(), item.min, item.max, 1));
			styleSettingComponent(spinner, tooltip);
			spinner.addChangeListener(event -> save(item.key, spinner.getValue()));
			result.add(spinner);
		}
		else if (value instanceof Color)
		{
			JButton colour = new JButton("Choose colour");
			colour.setBackground((Color) value);
			styleSettingComponent(colour, tooltip);
			colour.addActionListener(event ->
			{
				Color selected = JColorChooser.showDialog(panel, item.name,
					colour.getBackground());
				if (selected != null)
				{
					colour.setBackground(selected);
					save(item.key, selected);
				}
			});
			result.add(colour);
		}
		else
		{
			String initialValue = value == null ? "" : value.toString();
			JTextField text = new JTextField(initialValue);
			styleSettingComponent(text, tooltip);
			Runnable saveText = () ->
			{
				if (!rebuilding && text.isShowing()
					&& !initialValue.equals(text.getText()))
				{
					save(item.key, text.getText());
				}
			};
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
			JCheckBox checkbox = (JCheckBox) component;
			checkbox.setOpaque(false);
			checkbox.setForeground(Color.WHITE);
		}
	}

	private void save(String key, Object value)
	{
		try
		{
			settingsManager.save(key, value);
			refresh();
		}
		catch (IllegalStateException ex)
		{
			log.warn("Could not save side-panel setting {}", key, ex);
			JOptionPane.showMessageDialog(panel, ex.getMessage(), "Setting Not Saved",
				JOptionPane.ERROR_MESSAGE);
		}
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
		private final List<SidePanelSettingMetadata.Entry> items;

		private Section(String name, String key,
			List<SidePanelSettingMetadata.Entry> items)
		{
			this.name = name;
			this.key = key;
			this.items = items;
		}
	}
}
