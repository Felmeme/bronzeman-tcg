package com.bronzemantcg.settings;

import com.bronzemantcg.interop.BetaSaveImporter;
import com.bronzemantcg.panel.collection.BetaCollectionSnapshotService;
import com.bronzemantcg.panel.collection.PanelBetaCollectionViewModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.swing.BoxLayout;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.RuneLite;

/** User-initiated, local-only Beta history tools. These are not shareable gameplay settings. */
public final class BetaCardsSettings
{
	private final BetaCollectionSnapshotService snapshots;
	private final BetaSaveImporter importer;
	private final PanelBetaCollectionViewModel viewModel;
	private final Executor executor;
	private final BooleanSupplier disposed;
	private final Runnable changed;
	private final JPanel panel = new JPanel();
	private boolean busy;

	public BetaCardsSettings(BetaCollectionSnapshotService snapshots, BetaSaveImporter importer,
		PanelBetaCollectionViewModel viewModel, Executor executor, BooleanSupplier disposed,
		Runnable changed)
	{
		this.snapshots = snapshots;
		this.importer = importer;
		this.viewModel = viewModel;
		this.executor = executor;
		this.disposed = disposed;
		this.changed = changed;
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
	}

	public JPanel component()
	{
		refresh();
		return panel;
	}

	private void refresh()
	{
		panel.removeAll();
		JLabel label = new JLabel(busy ? "Working..." : statusText());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setForeground(net.runelite.client.ui.ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(label);
		button("Import Beta save...", this::selectSave, true);
		button("Wipe saved Beta snapshot", () -> confirmChange(false),
			snapshots.getStatus() != BetaCollectionSnapshotService.Status.CLEARED);
		button("Restore previous snapshot", () -> confirmChange(true), snapshots.canRestore());
		panel.revalidate();
		panel.repaint();
	}

	private String statusText()
	{
		BetaCollectionSnapshotService.SnapshotView view = snapshots.getView();
		switch (view.getStatus())
		{
			case IMPORTED: return "Imported: " + view.getOwnedNamesLowerCase().size() + " Beta names";
			case CLEARED: return "Cleared; automatic capture paused";
			case FROZEN_CAPTURED: return "Saved pre-v1 Beta history";
			case FROZEN_INFERRED: return "Beta history estimated from v1";
			case PROVISIONAL: return "Provisional Beta history";
			case INCOMPATIBLE: return "Saved Beta history is unreadable";
			default: return "No saved Beta history";
		}
	}

	private void button(String title, Runnable action, boolean enabled)
	{
		JButton button = new JButton(title);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		button.setEnabled(!busy && enabled);
		button.addActionListener(event -> action.run());
		panel.add(button);
	}

	private BetaCollectionSnapshotService.EditToken begin()
	{
		BetaCollectionSnapshotService.EditToken token = snapshots.beginEdit();
		if (token == null)
		{
			message("Log into the account whose Beta history you want to change first.");
		}
		return token;
	}

	private void selectSave()
	{
		BetaCollectionSnapshotService.EditToken token = begin();
		if (token == null)
		{
			return;
		}
		JFileChooser chooser = new JFileChooser(RuneLite.RUNELITE_DIR);
		chooser.setDialogTitle("Select an OSRS TCG Beta save or backup");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		configureChooser(chooser);
		// Keep all files selectable: historical backups may have a hash and no extension.
		if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION || disposed.getAsBoolean())
		{
			return;
		}
		Path selected = chooser.getSelectedFile().toPath();
		busy = true;
		refresh();
		submit(() ->
		{
			try
			{
				Set<String> names = importer.read(selected);
				Set<String> unmatched = snapshots.unmatchedNames(names);
				int parents = viewModel.prepare(names, BetaCollectionSnapshotService.Status.IMPORTED)
					.getOwnedParents();
				SwingUtilities.invokeLater(() -> preview(token, names, unmatched, parents));
			}
			catch (Exception ex)
			{
				finish("The selected file could not be imported. Check that it is a supported "
					+ "OSRS TCG save containing Beta cards. Nothing changed.");
			}
		});
	}

	/** Prefer the standard Details action without depending on a particular look-and-feel class. */
	static void configureChooser(JFileChooser chooser)
	{
		chooser.setPreferredSize(new Dimension(850, 600));
		Action details = chooser.getActionMap().get("viewTypeDetails");
		if (details != null && details.isEnabled())
		{
			details.actionPerformed(new ActionEvent(chooser, ActionEvent.ACTION_PERFORMED,
				"viewTypeDetails"));
		}
	}

	private void preview(BetaCollectionSnapshotService.EditToken token, Set<String> names,
		Set<String> unmatched, int parents)
	{
		if (disposed.getAsBoolean())
		{
			return;
		}
		if (!snapshots.isCurrent(token))
		{
			finish("The account or snapshot changed. Please start the import again.");
			return;
		}
		StringBuilder text = new StringBuilder()
			.append(names.size()).append(" unique Beta cards in this save.\n")
			.append(names.size() - unmatched.size()).append(" recognised cards; ")
			.append(parents).append(" catalogue parents.\n")
			.append(unmatched.size()).append(" unmatched names will be retained separately.\n");
		JTextArea preview = new JTextArea(text.toString(), 4, 36);
		preview.setEditable(false);
		preview.setLineWrap(true);
		preview.setWrapStyleWord(true);
		if (JOptionPane.showConfirmDialog(panel, new JScrollPane(preview), "Import Beta history?",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
		{
			finish(null);
			return;
		}
		apply(token, () -> snapshots.importNames(token, names));
	}

	private void confirmChange(boolean restore)
	{
		BetaCollectionSnapshotService.EditToken token = begin();
		if (token == null)
		{
			return;
		}
		String text = restore
			? "Restore the previous Bronzeman Beta snapshot for the currently active account?\n"
				+ "The current snapshot will become the undo copy."
			: "Clear Bronzeman's saved Beta history for the currently active account?\n"
				+ "It will stay cleared until you import or restore a snapshot.\n"
				+ "One previous copy is retained so you can undo this.\n"
				+ "OSRS TCG's save and collection are NOT deleted.";
		if (JOptionPane.showConfirmDialog(panel, text,
			restore ? "Restore Beta snapshot?" : "Wipe saved Beta snapshot?",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION)
		{
			busy = true;
			refresh();
			apply(token, () ->
			{
				if (restore)
				{
					snapshots.restore(token);
				}
				else
				{
					snapshots.wipe(token);
				}
			});
		}
	}

	private void apply(BetaCollectionSnapshotService.EditToken token, Runnable action)
	{
		submit(() ->
		{
			try
			{
				if (disposed.getAsBoolean() || !snapshots.isCurrent(token))
				{
					finish("The account or snapshot changed. Nothing was changed; please try again.");
					return;
				}
				action.run();
				finish(null);
			}
			catch (RuntimeException ex)
			{
				finish("The snapshot could not be changed. The account may have changed, "
					+ "or the saved data could not be read or written.");
			}
		});
	}

	private void submit(Runnable work)
	{
		try
		{
			executor.execute(work);
		}
		catch (RuntimeException ex)
		{
			finish("Beta history tools are unavailable while the plugin is stopping.");
		}
	}

	private void finish(String error)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (!disposed.getAsBoolean())
			{
				busy = false;
				refresh();
				changed.run();
				if (error != null)
				{
					message(error);
				}
			}
		});
	}

	private void message(String text)
	{
		JOptionPane.showMessageDialog(panel, text, "Beta Card Imports", JOptionPane.INFORMATION_MESSAGE);
	}
}
