---
name: file-issue
description: Use every time you open or update a GitHub issue for a Runeloader bug. Finds the root cause, sweeps the code for every other place with the same or a neighbouring defect, and files issues that cover all of them.
---

# File an issue with a sibling sweep

A fix that changes only the place where a bug showed up often leaves copies of the bug behind: the same logic in another source, another screen or the other build path. This skill makes the issue name every affected place before anyone starts the fix.

Run it before you create or extend any Bug issue. Keep the sweep small: about 15 minutes or 20 tool calls. It is research, not a fix.

## 1. Name the root cause

Read the code at the point of failure until you can say in one sentence which rule or computation is wrong. Name the rule, not only the symptom. For example, "the TOC lookup stops at the first suffix it finds in any folder", not "the addon shows the wrong version".

## 2. Sweep for every occurrence

Search current `main` for each kind of sibling below. Search for the method, the constant, the error text and the field names involved.

- **Copies of the computation.** Other places that compute the same thing their own way instead of through one helper.
- **Addon sources.** GitHub releases, the Forever catalog, CurseForge and WoWInterface (`Sources.java`, `Catalog.java`). A bug in one source's parsing, paging, version compare or download often exists in the others.
- **Install paths.** Fresh install, update, reinstall, delete, and an addon the user installed by hand (`Installer.java`, `Store.java`).
- **TOC variants.** `_Forever`, `_Camelot`, `_Mainline` and the base `.toc` (`Toc.java`).
- **Screens and input.** The installed, browse and settings tabs, the list and the detail panel, portrait and landscape, touch and gamepad (`MainActivity.java`, `Ui.java`).
- **Storage.** The app's own files and the copy in `_forever_/Interface/wow-addons.json`, and folders with and without the "all files" access.
- **Network.** Redirects, timeouts, no network, a server error, and the host check for the CurseForge key (`Http.java`, `ImageLoader.java`).
- **Android versions.** API 29 (scoped storage starts) up to 35. Check code that branches on `Build.VERSION.SDK_INT`.
- **Build paths.** Gradle and `build.sh`, when the bug is in the version, the manifest, resources or signing.
- **Edge cases of earlier fixes.** Check `git log -S<symbol>` and recent `bugfix/` merges on the same code for an off-by-one, an empty list or a first-match-wins lookup.

Sort each place you check into one of three groups:

- **same defect:** it goes into this issue's scope;
- **related but different:** it gets its own issue, linked from this one;
- **checked, not affected:** list it with the reason.

## 3. Check for duplicates

Search open issues and issues closed in the last two weeks by method, file and error text. If a closed fix did not hold, link it and say which place it missed.

## 4. Write the issue

Follow the rules in `AGENTS.md`: the issue type, the labels and the `Priority: P0|P1|P2` first line. The body has these sections:

- **Problem:** the root cause in one or two sentences, with the evidence (steps, a log, a screenshot, the addon and its version).
- **Where:** every affected `file:line` on current `main`, with the sibling places.
- **Scenario:** the inputs or the state that lead to the wrong behaviour.
- **Sweep:** what you searched, the same-defect places, the linked related issues, and the places checked and not affected.
- **Suggested fix:** prefer one shared helper that all places use over a separate patch at each place.
- **Tests:** a test that fails without the fix (a case in `test/InstallTest.java` for installer and TOC bugs), or the manual steps on an emulator for a UI bug.
- **Maintainability:** when the fix adds a shared helper or a table that later code must use or extend, name the rule for `AGENTS.md`. Otherwise write `None`.

## 5. When you fix an issue filed this way

Fix every place listed under **Where**. If the issue names a rule under **Maintainability**, add it to `AGENTS.md` in the same pull request. Before you open the pull request, run the sweep searches again on the branch. Fix a new place in the same pull request, or open its own issue and link it.

Report it in a `## Sweep` section of the pull request description:

- each place from **Where**, fixed;
- each new place the second sweep found: fixed here, or the new issue's number;
- the places you checked that are not affected, with the reason.
