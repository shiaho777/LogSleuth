# Filters & rules

A busy device produces thousands of lines a minute. LogSleuth narrows them
in three layers, and every layer applies to the list, to the recordings and
to the copy action at the same time.

## Level

The first chip on the filter bar is the level set. Tap it to toggle levels
individually — `V`, `D`, `I`, `W`, `E`, `F`, `A` — and the chip shows the
shortest description of what is selected (`≥ Warn`, `V+D+I+W+F+A`, and so on).

The stream screen's search matches on top of this: the level set decides which
lines exist, the query decides which of them are highlighted.

## The rule stack

The filter bar also carries the rules you have switched on. A rule is a saved
matcher — level, tag, query, app, exact tag, exclude, or regular expression
— and several can be active at once. Active rules appear as chips on the bar;
tap one to switch it off.

When the rules hide every line, the empty state offers to switch them all off
in one tap, instead of leaving you hunting for the chip.

## The app filter

**All apps** shows everything. Choosing one narrows the stream to a single
package. On the Shizuku and root paths the list can name the app, because
those paths can resolve the uid column to a package name.

## Making a filter from a line

Long-press a row to open its detail sheet, then **Create filter**. Instead of
silently saving a tag+pid rule, LogSleuth opens the filter editor prefilled
with the tag, pid, tid, uid, package, level and a truncated message. Drop
what you do not need, then save it as a rule or a preset.

The editor is also reachable from **Filters** in the filter bar, where you can
pick a package and toggle an allowed level set. Empty fields are ignored when
matching, so a rule with only a package set matches every line of that app.

## Search

The magnifier in the top bar switches the bar into a search field:

- the query is matched against the visible columns, with an optional
  case-sensitive toggle;
- the hit counter shows `3/57` and the steppers jump between hits;
- `Enter` on the keyboard jumps to the next hit.

Search highlights matches in place. It does not change what the buffer holds,
so clearing the query brings everything back.

## Presets

A preset is a saved filter you can apply with one tap. Presets are listed on
the **Filters** screen and can be enabled, disabled and edited. An enabled
preset behaves like a rule in the stack.

## What a recording keeps

A recording honours the **full** active filter — level set, query, tag,
exclude, regular expression and package — so what gets recorded matches what
you were looking at. Save it from the top bar, from the quick settings tile,
or from the floating controls.
