# Release notes

Every release ships a notes file, and the file is what ends up on the GitHub
Release page. The notes live in the repository so they are reviewed in the
version-bump pull request, next to the version bump itself — not written from
memory after the tag is pushed.

## The contract

- One file per version: `docs/releases/vX.Y.Z.md`.
- The tag `vX.Y.Z` must have a matching file, or the release workflow fails.
- The file is bilingual: English first, then a `<!-- zh-CN -->` marker and the
  Chinese text. GitHub renders the marker as an HTML comment, so the reader
  only sees the two languages.
- Sections are user-facing: **Added**, **Improved**, **Fixed**. A section with
  nothing in it is left out.
- Every bullet says what the user gets, not what the diff did, and ends with
  the pull request it came from: `(#124)`. If that pull request closed an
  issue, write `(#124, closes #121)`.
- The first line is a one-line summary of the release, in both languages.

## How to write one

```bash
# The merged pull requests since the last tag:
python3 scripts/draft_release_notes.py --list

# A skeleton with the version numbers filled in:
python3 scripts/draft_release_notes.py > docs/releases/v0.3.6.md
```

Then rewrite each bullet in your own words. The script collects the merged
pull requests; it does not write the sentences for you, because "the scroll
rail now shows the whole buffer" and "add LogScrollRail" are not the same
thing for the person reading the release.

## Why the notes live in the repo

The alternative — letting the workflow generate them from the commit log —
produces a list of commit subjects in English only, which reads like a git log
and tells the user nothing about what changed for them. A committed file can
be reviewed before it ships, translated, and kept next to the version it
describes.
