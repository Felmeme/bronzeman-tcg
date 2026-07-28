#!/usr/bin/env node

/**
 * Shared OSRS Wiki fetcher for the data generators.
 *
 * Wraps the wiki etiquette this project committed to (agreed with wiki staff,
 * github issue #1, 2026-07-18) so every generator honours it identically instead
 * of rolling its own fetch:
 *
 *   1. PLAIN PAGE URLS ONLY, no query parameters. Those hit the wiki's edge
 *      cache, which is what their infrastructure is optimised for. api.php
 *      (?action=parse) forces an uncached server-side parse per call and is
 *      refused outright here.
 *   2. A descriptive User-Agent naming this project, with a contact URL.
 *   3. Pacing: at least MIN_INTERVAL_MS between NETWORK requests. Cache hits are
 *      free and are not paced.
 *   4. Cache every raw response to scripts/wiki_cache/<Page_Title>.cache, and
 *      read from there first, so re-running a generator hits the wiki zero times.
 *
 * Parsing is deliberately NOT done here - callers parse the returned HTML
 * locally, which is what the existing generators already do.
 *
 * Use as a module:
 *   const { fetchPage, fetchPages } = require("./wiki_fetch");
 *   const html = await fetchPage("Lobster_pot");
 *
 * Use from the command line (warms the cache ahead of a generator run):
 *   node scripts/wiki_fetch.js Lobster_pot "Konar quo Maten"
 *   node scripts/wiki_fetch.js --refresh Lobster_pot
 *   node scripts/wiki_fetch.js --list pages.txt
 */

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const CACHE_DIR = path.join(ROOT, "scripts", "wiki_cache");
const BASE = "https://oldschool.runescape.wiki/w/";

// Names this project and gives wiki staff somewhere to look. Keep the repo URL
// current - it is the contact route promised in the etiquette agreement.
const USER_AGENT =
  "bronzeman-tcg-datagen/1.0 (RuneLite plugin restriction data; " +
  "+https://github.com/Felmeme/bronzeman-tcg)";

// ~1 request/second. Deliberately a floor, not a target.
const MIN_INTERVAL_MS = 1100;
const TIMEOUT_MS = 30000;
const MAX_RETRIES = 3;

let lastRequestMs = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Wiki page titles use underscores. Accept either form, and refuse anything
 * that would smuggle in a query string, an api.php call, or a path traversal
 * into the cache directory.
 */
function normalizeTitle(rawTitle) {
  if (typeof rawTitle !== "string" || !rawTitle.trim()) {
    throw new Error("Page title must be a non-empty string");
  }
  const title = rawTitle.trim().replace(/\s+/g, "_");

  if (/[?&#]/.test(title)) {
    throw new Error(
      `Refusing "${rawTitle}": query parameters bypass the wiki's edge cache. ` +
        "Request a plain page title only."
    );
  }
  if (/api\.php/i.test(title)) {
    throw new Error(
      `Refusing "${rawTitle}": api.php forces an uncached server-side parse. ` +
        "Fetch the plain page and parse its HTML locally."
    );
  }
  if (title.includes("..") || title.includes("\\") || path.isAbsolute(title)) {
    throw new Error(`Refusing "${rawTitle}": unsafe cache filename`);
  }
  return title;
}

/** Cache path for a page title. Subpage slashes flatten so the dir stays flat. */
function cachePathFor(rawTitle) {
  const safe = normalizeTitle(rawTitle).replace(/\//g, "__");
  return path.join(CACHE_DIR, `${safe}.cache`);
}

function readCache(rawTitle) {
  const file = cachePathFor(rawTitle);
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : null;
}

/** Enforce the pacing floor between network requests only. */
async function pace() {
  const waitMs = lastRequestMs + MIN_INTERVAL_MS - Date.now();
  if (waitMs > 0) {
    await sleep(waitMs);
  }
  lastRequestMs = Date.now();
}

async function requestOnce(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      headers: { "User-Agent": USER_AGENT, Accept: "text/html" },
      redirect: "follow",
      signal: controller.signal,
    });
    const body = response.ok ? await response.text() : "";
    return { status: response.status, ok: response.ok, body };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Fetch one page, cache-first.
 *
 * @param {string} rawTitle  plain wiki page title ("Lobster pot" or "Lobster_pot")
 * @param {{refresh?: boolean, quiet?: boolean}} options
 * @returns {Promise<string>} the raw page HTML
 */
async function fetchPage(rawTitle, options = {}) {
  const title = normalizeTitle(rawTitle);
  const file = cachePathFor(title);

  if (!options.refresh) {
    const cached = readCache(title);
    if (cached !== null) {
      if (!options.quiet) {
        console.log(`cache  ${title}`);
      }
      return cached;
    }
  }

  fs.mkdirSync(CACHE_DIR, { recursive: true });
  const url = BASE + encodeURIComponent(title).replace(/%2F/g, "/");

  let lastError = null;
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    await pace();
    let result;
    try {
      result = await requestOnce(url);
    } catch (error) {
      lastError = error;
      // Network blip or timeout: back off progressively rather than hammering.
      await sleep(MIN_INTERVAL_MS * attempt * 2);
      continue;
    }

    if (result.ok) {
      // Write only complete, successful bodies - a truncated cache entry would
      // silently poison every later run, which is the failure this whole
      // pipeline exists to avoid.
      fs.writeFileSync(file, result.body, "utf8");
      if (!options.quiet) {
        console.log(`fetch  ${title}  (${result.body.length} bytes)`);
      }
      return result.body;
    }

    if (result.status === 404) {
      throw new Error(`${title}: 404 - page does not exist (check the exact title)`);
    }
    lastError = new Error(`${title}: HTTP ${result.status}`);
    if (result.status === 429 || result.status >= 500) {
      // Rate-limited or server-side: back off hard. Being a good citizen here
      // is the whole point of the etiquette agreement.
      await sleep(MIN_INTERVAL_MS * attempt * 5);
      continue;
    }
    break;
  }
  throw lastError || new Error(`${title}: fetch failed`);
}

/**
 * Fetch many pages in order, paced. Sequential on purpose - concurrency would
 * defeat the pacing floor.
 *
 * @returns {Promise<Map<string, string>>} title -> HTML (failures are reported
 *          and omitted, so one bad title cannot abandon a long run)
 */
async function fetchPages(titles, options = {}) {
  const results = new Map();
  const failures = [];
  for (const title of titles) {
    try {
      results.set(normalizeTitle(title), await fetchPage(title, options));
    } catch (error) {
      failures.push(`${title}: ${error.message}`);
      console.error(`FAIL   ${title}: ${error.message}`);
    }
  }
  if (failures.length) {
    console.error(`\n${failures.length} page(s) failed:`);
    failures.forEach((line) => console.error(`  - ${line}`));
  }
  return results;
}

module.exports = { fetchPage, fetchPages, cachePathFor, readCache, USER_AGENT };

if (require.main === module) {
  const args = process.argv.slice(2);
  const refresh = args.includes("--refresh");
  const listIndex = args.indexOf("--list");

  let titles = args.filter((arg) => !arg.startsWith("--"));
  if (listIndex !== -1) {
    const listFile = args[listIndex + 1];
    if (!listFile) {
      console.error("--list needs a file path");
      process.exit(1);
    }
    titles = fs
      .readFileSync(path.resolve(listFile), "utf8")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith("#"));
  }

  if (!titles.length) {
    console.error(
      "usage: node scripts/wiki_fetch.js [--refresh] <page title> [...]\n" +
        "       node scripts/wiki_fetch.js [--refresh] --list <file>"
    );
    process.exit(1);
  }

  fetchPages(titles, { refresh })
    .then((pages) => {
      console.log(`\n${pages.size}/${titles.length} page(s) available in ${CACHE_DIR}`);
      process.exit(pages.size === titles.length ? 0 : 1);
    })
    .catch((error) => {
      console.error(error);
      process.exit(1);
    });
}
