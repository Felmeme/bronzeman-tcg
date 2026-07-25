import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.DBTableID;
import net.runelite.cache.DBRowManager;
import net.runelite.cache.definitions.DBRowDefinition;
import net.runelite.cache.fs.Store;

/**
 * Developer-only local-cache snapshot generator for Slayer master assignments.
 *
 * This reads Jagex's SlayerMasterTask, SlayerTask and SlayerArea database rows
 * from the local oldschool/LIVE cache. It is not part of the plugin build and
 * never runs when the plugin launches.
 */
public final class SlayerCacheDump
{
	private static final Map<Integer, String> MASTER_NAMES = Map.of(
		1, "Turael",
		2, "Mazchna",
		3, "Vannaka",
		4, "Chaeldar",
		5, "Duradel",
		6, "Nieve",
		7, "Krystilia",
		8, "Konar quo Maten",
		9, "Spria");

	private SlayerCacheDump()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException(
				"Usage: SlayerCacheDump <oldschool/LIVE cache> <output.json>");
		}

		List<Assignment> assignments = new ArrayList<>();
		try (Store store = new Store(new File(args[0])))
		{
			store.load();
			DBRowManager rows = new DBRowManager(store);
			rows.load();

			Map<Integer, DBRowDefinition> tasks = rowsForTable(rows, DBTableID.SlayerTask.ID);
			Map<Integer, DBRowDefinition> areas = rowsForTable(rows, DBTableID.SlayerArea.ID);

			for (DBRowDefinition row : rows.getRows())
			{
				if (row.getTableId() != DBTableID.SlayerMasterTask.ID)
				{
					continue;
				}

				int masterId = firstInt(row, DBTableID.SlayerMasterTask.COL_MASTER_ID, -1);
				String master = MASTER_NAMES.get(masterId);
				if (master == null)
				{
					// Master id 10 is the temporary Leagues assignment table, not an
					// Old School main-game Slayer master.
					if (masterId == 10)
					{
						continue;
					}
					throw new IllegalStateException("Unknown Slayer master id " + masterId
						+ " in row " + row.getId());
				}

				int taskRowId = firstInt(row, DBTableID.SlayerMasterTask.COL_TASK, -1);
				DBRowDefinition task = tasks.get(taskRowId);
				if (task == null)
				{
					throw new IllegalStateException("Missing Slayer task row " + taskRowId);
				}

				List<Area> assignmentAreas = new ArrayList<>();
				for (int areaRowId : ints(row, DBTableID.SlayerMasterTask.COL_AREAS))
				{
					DBRowDefinition area = areas.get(areaRowId);
					if (area == null)
					{
						throw new IllegalStateException("Missing Slayer area row " + areaRowId);
					}
					assignmentAreas.add(new Area(areaRowId,
						firstString(area, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER)));
				}

				assignments.add(new Assignment(
					master,
					masterId,
					row.getId(),
					taskRowId,
					firstString(task, DBTableID.SlayerTask.COL_NAME_UPPERCASE),
					firstInt(row, DBTableID.SlayerMasterTask.COL_WEIGHT, 0),
					firstInt(row, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT, 0),
					firstInt(row, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT, 0),
					firstInt(task, DBTableID.SlayerTask.COL_MIN_COMLEVEL, 0),
					firstInt(row, DBTableID.SlayerMasterTask.COL_TASK_UNLOCK, 0),
					assignmentAreas));
			}
		}

		assignments.sort(Comparator
			.comparing((Assignment assignment) -> assignment.master)
			.thenComparing(assignment -> assignment.task, String.CASE_INSENSITIVE_ORDER));
		Map<String, List<String>> runeLiteTargets = loadRuneLiteTaskTargets();
		Path output = Path.of(args[1]);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, toJson(assignments, runeLiteTargets), StandardCharsets.UTF_8);
		System.out.println("Wrote " + assignments.size() + " Slayer master-task rows to " + output);
	}

	/**
	 * RuneLite's package-private Task enum is useful as a broad "technically counts"
	 * candidate list, but it is deliberately not used by the shipped plugin. Reflection
	 * keeps this developer tool independent of that internal compile-time type.
	 */
	private static Map<String, List<String>> loadRuneLiteTaskTargets() throws Exception
	{
		Class<?> taskClass = Class.forName("net.runelite.client.plugins.slayer.Task");
		Method valuesMethod = taskClass.getDeclaredMethod("values");
		Field nameField = taskClass.getDeclaredField("name");
		Field targetsField = taskClass.getDeclaredField("targetNames");
		valuesMethod.setAccessible(true);
		nameField.setAccessible(true);
		targetsField.setAccessible(true);

		Map<String, List<String>> result = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Object task : (Object[]) valuesMethod.invoke(null))
		{
			String name = (String) nameField.get(task);
			String[] targets = (String[]) targetsField.get(task);
			result.put(name, List.of(targets));
		}
		return result;
	}

	private static Map<Integer, DBRowDefinition> rowsForTable(DBRowManager rows, int tableId)
	{
		Map<Integer, DBRowDefinition> result = new HashMap<>();
		for (DBRowDefinition row : rows.getRows())
		{
			if (row.getTableId() == tableId)
			{
				result.put(row.getId(), row);
			}
		}
		return result;
	}

	private static int firstInt(DBRowDefinition row, int column, int defaultValue)
	{
		List<Integer> values = ints(row, column);
		return values.isEmpty() ? defaultValue : values.get(0);
	}

	private static List<Integer> ints(DBRowDefinition row, int column)
	{
		List<Integer> result = new ArrayList<>();
		Object[][] columns = row.getColumnValues();
		if (columns == null || column < 0 || column >= columns.length || columns[column] == null)
		{
			return result;
		}
		for (Object value : columns[column])
		{
			if (value instanceof Integer)
			{
				result.add((Integer) value);
			}
		}
		return result;
	}

	private static String firstString(DBRowDefinition row, int column)
	{
		Object[][] columns = row.getColumnValues();
		if (columns == null || column < 0 || column >= columns.length || columns[column] == null)
		{
			return "";
		}
		for (Object value : columns[column])
		{
			if (value instanceof String)
			{
				return (String) value;
			}
		}
		return "";
	}

	private static String toJson(List<Assignment> assignments,
		Map<String, List<String>> runeLiteTargets)
	{
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("\t\"source\": \"RuneLite local cache DBTableID.SlayerMasterTask/SlayerTask/SlayerArea\",\n");
		json.append("\t\"assignments\": [\n");
		for (int i = 0; i < assignments.size(); i++)
		{
			Assignment assignment = assignments.get(i);
			json.append("\t\t{\n");
			field(json, "master", assignment.master, true, 3);
			numberField(json, "masterId", assignment.masterId, true, 3);
			numberField(json, "rowId", assignment.rowId, true, 3);
			numberField(json, "taskRowId", assignment.taskRowId, true, 3);
			field(json, "task", assignment.task, true, 3);
			numberField(json, "weight", assignment.weight, true, 3);
			numberField(json, "minAmount", assignment.minAmount, true, 3);
			numberField(json, "maxAmount", assignment.maxAmount, true, 3);
			numberField(json, "minCombat", assignment.minCombat, true, 3);
			numberField(json, "unlockRowId", assignment.unlockRowId, true, 3);
			json.append("\t\t\t\"areas\": [");
			for (int j = 0; j < assignment.areas.size(); j++)
			{
				Area area = assignment.areas.get(j);
				if (j > 0)
				{
					json.append(", ");
				}
				json.append("{\"rowId\": ").append(area.rowId)
					.append(", \"name\": \"").append(escape(area.name)).append("\"}");
			}
			json.append("]\n");
			json.append("\t\t}");
			if (i + 1 < assignments.size())
			{
				json.append(',');
			}
			json.append('\n');
		}
		json.append("\t],\n");
		json.append("\t\"runeLiteTaskTargets\": {\n");
		int taskIndex = 0;
		for (Map.Entry<String, List<String>> entry : runeLiteTargets.entrySet())
		{
			json.append("\t\t\"").append(escape(entry.getKey())).append("\": [");
			for (int i = 0; i < entry.getValue().size(); i++)
			{
				if (i > 0)
				{
					json.append(", ");
				}
				json.append('"').append(escape(entry.getValue().get(i))).append('"');
			}
			json.append(']');
			if (++taskIndex < runeLiteTargets.size())
			{
				json.append(',');
			}
			json.append('\n');
		}
		json.append("\t}\n");
		json.append("}\n");
		return json.toString();
	}

	private static void field(StringBuilder json, String name, String value,
		boolean comma, int tabs)
	{
		indent(json, tabs);
		json.append('"').append(name).append("\": \"").append(escape(value)).append('"');
		if (comma)
		{
			json.append(',');
		}
		json.append('\n');
	}

	private static void numberField(StringBuilder json, String name, int value,
		boolean comma, int tabs)
	{
		indent(json, tabs);
		json.append('"').append(name).append("\": ").append(value);
		if (comma)
		{
			json.append(',');
		}
		json.append('\n');
	}

	private static void indent(StringBuilder json, int tabs)
	{
		for (int i = 0; i < tabs; i++)
		{
			json.append('\t');
		}
	}

	private static String escape(String value)
	{
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\r", "\\r")
			.replace("\n", "\\n")
			.replace("\t", "\\t");
	}

	private static final class Assignment
	{
		private final String master;
		private final int masterId;
		private final int rowId;
		private final int taskRowId;
		private final String task;
		private final int weight;
		private final int minAmount;
		private final int maxAmount;
		private final int minCombat;
		private final int unlockRowId;
		private final List<Area> areas;

		private Assignment(String master, int masterId, int rowId, int taskRowId,
			String task, int weight, int minAmount, int maxAmount, int minCombat,
			int unlockRowId, List<Area> areas)
		{
			this.master = master;
			this.masterId = masterId;
			this.rowId = rowId;
			this.taskRowId = taskRowId;
			this.task = task;
			this.weight = weight;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			this.minCombat = minCombat;
			this.unlockRowId = unlockRowId;
			this.areas = areas;
		}
	}

	private static final class Area
	{
		private final int rowId;
		private final String name;

		private Area(int rowId, String name)
		{
			this.rowId = rowId;
			this.name = name;
		}
	}
}
