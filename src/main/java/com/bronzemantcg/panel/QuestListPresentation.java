package com.bronzemantcg.panel;

import com.bronzemantcg.catalog.QuestCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Presentation-only projection which removes NPC-backed quest requirements. */
final class QuestListPresentation
{
	private QuestListPresentation()
	{
	}

	static List<QuestCatalog.QuestEntry> hideNpcRequirements(
		List<QuestCatalog.QuestEntry> entries)
	{
		List<QuestCatalog.QuestEntry> projected = new ArrayList<>();
		for (QuestCatalog.QuestEntry entry : entries)
		{
			List<QuestCatalog.Section> sections = new ArrayList<>();
			for (QuestCatalog.Section section : entry.sections)
			{
				List<QuestCatalog.Requirement> requirements = new ArrayList<>();
				for (QuestCatalog.Requirement requirement : section.requirements)
				{
					Projection result = project(requirement);
					if (!result.hidden)
					{
						requirements.add(result.requirement);
					}
				}
				if (!requirements.isEmpty())
				{
					sections.add(section.withRequirements(requirements));
				}
			}
			projected.add(entry.withSections(sections));
		}
		return Collections.unmodifiableList(projected);
	}

	static boolean isNpcRequirement(QuestCatalog.Requirement requirement)
	{
		return requirement != null
			&& ("npc".equals(requirement.type) || "enemy".equals(requirement.type));
	}

	private static Projection project(QuestCatalog.Requirement source)
	{
		if (isNpcRequirement(source))
		{
			return Projection.hidden();
		}

		List<QuestCatalog.Requirement> children = new ArrayList<>();
		boolean hiddenChild = false;
		boolean visibleChild = false;
		boolean routed = !source.selector.isEmpty();
		for (QuestCatalog.Requirement child : source.children)
		{
			Projection projected = project(child);
			if (projected.hidden)
			{
				hiddenChild = true;
				if (routed)
				{
					// Retain an empty route marker so selecting a route whose only
					// requirements were hidden remains satisfied.
					children.add(child.withProjection(Collections.emptyList(),
						Collections.emptyList(), Collections.emptyList()));
				}
			}
			else
			{
				visibleChild = true;
				children.add(projected.requirement);
			}
		}

		if (!source.children.isEmpty())
		{
			if (routed && hiddenChild && !visibleChild)
			{
				return Projection.hidden();
			}
			if (!routed && ((source.logic == QuestCatalog.Logic.ANY && hiddenChild)
				|| (source.logic == QuestCatalog.Logic.ALL && children.isEmpty())))
			{
				return Projection.hidden();
			}
			return Projection.visible(source.withProjection(
				source.displayCards, source.lowerCards, children));
		}
		return Projection.visible(source);
	}

	private static final class Projection
	{
		private final QuestCatalog.Requirement requirement;
		private final boolean hidden;

		private Projection(QuestCatalog.Requirement requirement, boolean hidden)
		{
			this.requirement = requirement;
			this.hidden = hidden;
		}

		private static Projection visible(QuestCatalog.Requirement requirement)
		{
			return new Projection(requirement, false);
		}

		private static Projection hidden()
		{
			return new Projection(null, true);
		}
	}
}
