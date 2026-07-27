#!/usr/bin/env node
/**
 * Headless harness for the Bronzeman TCG RuneLite plugin.
 *
 * The shipped "app" is a plugin inside the RuneLite game client, so it cannot be driven
 * headlessly end-to-end (it needs a logged-in OSRS account + the OSRS TCG plugin). What
 * CAN be driven - and what nearly every change here actually touches - is the restriction
 * DATA and the lookup logic that reads it. This script mirrors the two Java catalogs'
 * real key-building rules so you can validate and interrogate that layer in ~1 second.
 *
 * Bugs this class of check has caught in the wild:
 *  - 8 "Crossbow stock" rules collapsing to one key, so every tier demanded Magic cards
 *  - needle-on-leather demanding Leather chaps for all 6 products (false block)
 *  - iron ore -> furnace ambiguity making a plain Iron bar demand coal
 *  - typo'd card names ("draongstone bolt tips") that silently never matched
 *
 * Commands:
 *   node driver.mjs check        validate + collisions, exit 1 on problems (use in CI/pre-commit)
 *   node driver.mjs validate     every card reference exact-matches the card catalogs
 *   node driver.mjs collisions   lookup keys shared by rules with DIFFERENT requirements
 *   node driver.mjs explain <kind> <name> [target]   what a given click requires
 *   node driver.mjs stats        counts per resource / category
 *
 * Zero dependencies. Node >= 18.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------- repo discovery
function findRoot() {
  const marker = 'runelite-plugin.properties';
  const starts = [process.cwd(), path.dirname(fileURLToPath(import.meta.url))];
  for (const start of starts) {
    let d = start;
    for (let i = 0; i < 8; i++) {
      if (fs.existsSync(path.join(d, marker))) return d;
      const up = path.dirname(d);
      if (up === d) break;
      d = up;
    }
  }
  console.error('Could not locate repo root (no runelite-plugin.properties found).');
  process.exit(2);
}
const ROOT = findRoot();
const RES = path.join(ROOT, 'src', 'main', 'resources');

/** Gson tolerates trailing commas; strict JSON.parse does not. Data files have shipped
 *  with them before (quest_cards.json), so parse leniently rather than crashing. */
function readJson(file) {
  const full = path.join(RES, file);
  if (!fs.existsSync(full)) return null;
  const raw = fs.readFileSync(full, 'utf8');
  try {
    return JSON.parse(raw);
  } catch {
    return JSON.parse(raw.replace(/,(\s*[}\]])/g, '$1'));
  }
}

const lc = (s) => String(s).trim().toLowerCase();

// ---------------------------------------------------------------- card catalogs
function catalogs() {
  const cards = new Set();
  const entities = new Set();
  for (const f of ['tracked_item_names.json', 'tracked_monster_names.json']) {
    const j = readJson(f);
    if (!j?.entityToCards) continue;
    for (const [entity, list] of Object.entries(j.entityToCards)) {
      entities.add(lc(entity));
      for (const c of list) cards.add(lc(c));
    }
  }
  return { cards, entities };
}

// ---------------------------------------------------------------- card references
/** Every place a card name is referenced, as {file, where, name}. */
function cardRefs() {
  const refs = [];
  const push = (file, where, name) => {
    if (typeof name === 'string' && name.trim()) refs.push({ file, where, name: name.trim() });
  };

  const rn = readJson('resource_nodes.json');
  for (const n of rn?.nodes ?? []) {
    const where = `${n.category}/${n.kind}/${n.name}`;
    for (const c of n.requiredCards ?? []) push('resource_nodes', where, c);
    for (const g of n.requiredCardGroups ?? []) for (const c of g) push('resource_nodes', where, c);
  }
  for (const c of rn?.masterFarmerSeedCards ?? []) push('resource_nodes', 'masterFarmerSeedCards', c);

  for (const r of readJson('recipe_nodes.json')?.recipes ?? []) {
    const where = `${r.category}/${r.trigger?.kind}/${r.trigger?.name}`;
    for (const g of r.inputs ?? []) for (const c of g) push('recipe_nodes', where, c);
    if (r.output) push('recipe_nodes', where, r.output);
  }

  for (const q of readJson('quest_cards.json')?.quests ?? []) {
    for (const g of q.cardGroups ?? []) for (const c of g) push('quest_cards', q.name, c);
    for (const c of q.monsterCards ?? []) push('quest_cards', q.name, c);
  }

  for (const ct of readJson('content_cards.json')?.contents ?? []) {
    for (const c of ct.monsterCards ?? []) push('content_cards', ct.name, c);
  }

  for (const a of readJson('monster_areas.json')?.areas ?? []) {
    for (const c of a.monsterCards ?? []) push('monster_areas', a.name, c);
  }

  for (const cat of readJson('important_unlocks.json')?.categories ?? []) {
    for (const c of cat.items ?? []) push('important_unlocks', cat.name, c);
    for (const sub of cat.subcategories ?? []) {
      for (const c of sub.items ?? []) push('important_unlocks', `${cat.name}/${sub.name}`, c);
    }
  }

  const cons = readJson('consumables.json');
  for (const key of ['food', 'potions']) {
    for (const c of cons?.[key] ?? []) push('consumables', key, c);
  }
  return refs;
}

// ---------------------------------------------------------------- lookup keys
/**
 * Mirrors RecipeCatalog.load(): key = kind|name|target, targets defaulting to "*".
 * An interface product ALSO gets the name|* catch-all, but ONLY when that interface name
 * is unique - shared names (the knife menu labels every tier "Crossbow stock") must match
 * on their declared target instead, or they all collapse onto one key.
 */
function recipeKeys(recipes) {
  const ifaceCount = new Map();
  for (const r of recipes) {
    if (lc(r.trigger?.kind) === 'interface') {
      const n = lc(r.trigger.name);
      ifaceCount.set(n, (ifaceCount.get(n) ?? 0) + 1);
    }
  }
  const out = [];
  for (const r of recipes) {
    const kind = lc(r.trigger?.kind), name = lc(r.trigger?.name);
    const targets = (r.trigger?.targets?.length ? r.trigger.targets : ['*']);
    const keys = new Set(targets.map((t) => `${kind}|${name}|${lc(t ?? '*')}`));
    if (kind === 'spell-on-item') for (const t of targets) keys.add(`${kind}|${lc(t)}|*`);
    if (kind === 'interface' && (ifaceCount.get(name) ?? 0) <= 1) keys.add(`${kind}|${name}|*`);
    out.push({ rule: r, keys: [...keys], sig: JSON.stringify([r.inputs ?? [], r.output ?? null]) });
  }
  return out;
}

/** Mirrors ResourceNodeCatalog.load(): key = kind|name|option, one per declared option. */
function nodeKeys(nodes) {
  return nodes.map((n) => {
    const kind = lc(n.kind), name = lc(n.name);
    const opts = (n.options?.length ? n.options : ['*']);
    return {
      rule: n,
      keys: [...new Set(opts.map((o) => `${kind}|${name}|${lc(o ?? '*')}`))],
      sig: JSON.stringify([n.requiredCardGroups ?? n.requiredCards ?? [], n.groupRoles ?? []]),
    };
  });
}

// ---------------------------------------------------------------- commands
function cmdValidate() {
  const { cards, entities } = catalogs();
  const refs = cardRefs();
  const unknown = [], entityOnly = [];
  for (const r of refs) {
    const n = lc(r.name);
    if (cards.has(n)) continue;
    (entities.has(n) ? entityOnly : unknown).push(r);
  }
  console.log(`card references checked : ${refs.length}`);
  console.log(`known card names        : ${cards.size}`);
  if (entityOnly.length) {
    console.log(`\nNOTE - matches an entity name but not a card name (${entityOnly.length}):`);
    for (const r of entityOnly.slice(0, 20)) console.log(`  ${r.file} [${r.where}] "${r.name}"`);
    if (entityOnly.length > 20) console.log(`  ...and ${entityOnly.length - 20} more`);
  }
  if (unknown.length) {
    console.log(`\nFAIL - not a card at all, so these can never be satisfied (${unknown.length}):`);
    for (const r of unknown.slice(0, 40)) console.log(`  ${r.file} [${r.where}] "${r.name}"`);
    if (unknown.length > 40) console.log(`  ...and ${unknown.length - 40} more`);
  } else {
    console.log('\nOK - every card reference matches a real card name.');
  }
  return unknown.length;
}

function cmdCollisions() {
  const recipes = readJson('recipe_nodes.json')?.recipes ?? [];
  const nodes = readJson('resource_nodes.json')?.nodes ?? [];
  let problems = 0;
  for (const [label, entries] of [['recipe', recipeKeys(recipes)], ['node', nodeKeys(nodes)]]) {
    const byKey = new Map();
    for (const e of entries) for (const k of e.keys) {
      if (!byKey.has(k)) byKey.set(k, []);
      byKey.get(k).push(e);
    }
    const clashes = [...byKey.entries()].filter(([, v]) => new Set(v.map((x) => x.sig)).size > 1);
    console.log(`${label} lookup keys: ${byKey.size} | keys shared by differing rules: ${clashes.length}`);
    for (const [key, v] of clashes) {
      problems++;
      console.log(`  CLASH ${key}  (${v.length} rules, only the LAST is reachable)`);
      for (const x of v) {
        const r = x.rule;
        console.log(`     -> ${JSON.stringify(r.output ?? r.requiredCardGroups ?? r.requiredCards)}`);
      }
    }
  }
  console.log(problems ? '\nFAIL - see clashes above.' : '\nOK - no key collisions.');
  return problems;
}

function cmdExplain(kind, name, target) {
  if (!kind || !name) {
    console.error('usage: driver.mjs explain <kind> <name> [target]');
    console.error('  kinds: interface | item-on-item | item-on-object | object | npc | fishing-spot | inventory');
    return 2;
  }
  const k = lc(kind), n = lc(name), t = target ? lc(target) : '*';
  let hits = 0;

  const recipes = readJson('recipe_nodes.json')?.recipes ?? [];
  const rmap = new Map();
  for (const e of recipeKeys(recipes)) for (const key of e.keys) rmap.set(key, e.rule);
  const recipe = rmap.get(`${k}|${n}|${t}`) ?? (t !== '*' ? rmap.get(`${k}|${n}|*`) : undefined);
  if (recipe) {
    hits++;
    console.log(`RECIPE  category=${recipe.category}`);
    console.log(`  inputs (all groups, any card within a group):`);
    for (const g of recipe.inputs ?? []) console.log(`    - ${g.join(' / ')}`);
    console.log(`  output: ${recipe.output ?? '(none)'}`);
    if (recipe.notes) console.log(`  notes: ${recipe.notes}`);
  }

  const nodes = readJson('resource_nodes.json')?.nodes ?? [];
  const nmap = new Map();
  for (const e of nodeKeys(nodes)) for (const key of e.keys) nmap.set(key, e.rule);
  const node = nmap.get(`${k}|${n}|${t}`) ?? (t !== '*' ? nmap.get(`${k}|${n}|*`) : undefined);
  if (node) {
    hits++;
    console.log(`NODE    category=${node.category}  options=${JSON.stringify(node.options)}`);
    const groups = node.requiredCardGroups ?? (node.requiredCards ?? []).map((c) => [c]);
    const roles = node.groupRoles ?? [];
    groups.forEach((g, i) => console.log(`    - [${roles[i] || 'no role'}] ${g.join(' / ')}`));
    if (node.notes) console.log(`  notes: ${node.notes}`);
  }

  if (!hits) console.log('no rule matches -> this interaction is NOT restricted');
  return 0;
}

function cmdStats() {
  const count = (arr) => arr?.length ?? 0;
  const rn = readJson('resource_nodes.json'), rc = readJson('recipe_nodes.json');
  const byCat = (arr, get) => {
    const m = new Map();
    for (const x of arr ?? []) m.set(get(x), (m.get(get(x)) ?? 0) + 1);
    return [...m.entries()].sort((a, b) => b[1] - a[1]);
  };
  const { cards } = catalogs();
  console.log(`cards in catalogs      : ${cards.size}`);
  console.log(`resource_nodes.nodes   : ${count(rn?.nodes)}`);
  console.log(`recipe_nodes.recipes   : ${count(rc?.recipes)}`);
  console.log(`quests                 : ${count(readJson('quest_cards.json')?.quests)}`);
  console.log(`pvm contents           : ${count(readJson('content_cards.json')?.contents)}`);
  console.log(`monster areas          : ${count(readJson('monster_areas.json')?.areas)}`);
  console.log(`important_unlocks cats : ${count(readJson('important_unlocks.json')?.categories)}`);
  console.log(`card references total  : ${cardRefs().length}`);
  console.log('\nnodes by category:');
  for (const [c, n] of byCat(rn?.nodes, (x) => x.category)) console.log(`  ${String(c).padEnd(22)} ${n}`);
  console.log('\nrecipes by category:');
  for (const [c, n] of byCat(rc?.recipes, (x) => x.category)) console.log(`  ${String(c).padEnd(22)} ${n}`);
  return 0;
}

// ---------------------------------------------------------------- main
const [cmd, ...args] = process.argv.slice(2);
let exit = 0;
switch (cmd ?? 'check') {
  case 'check':
    exit = (cmdValidate() ? 1 : 0);
    console.log('');
    exit = (cmdCollisions() ? 1 : exit);
    break;
  case 'validate': exit = cmdValidate() ? 1 : 0; break;
  case 'collisions': exit = cmdCollisions() ? 1 : 0; break;
  case 'explain': exit = cmdExplain(...args); break;
  case 'stats': exit = cmdStats(); break;
  default:
    console.error(`unknown command "${cmd}"`);
    console.error('commands: check | validate | collisions | explain <kind> <name> [target] | stats');
    exit = 2;
}
process.exit(exit);
