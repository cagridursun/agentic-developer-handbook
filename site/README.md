# Interactive learning site

A static, local, backend-free learning layer over the handbook. Version 1 covers one experience: **Capstone 01 — Build a Small Agentic System**.

Boundaries, on purpose:

- No framework, no build step, no npm — semantic HTML, modern CSS, small ES modules
- No backend, no accounts, no analytics — progress lives only in your browser's `localStorage` (key `adh.learning.v1.capstone01`)
- The canonical content stays in the repository: Markdown, Java code, and `DECISIONS.md`. The site is an interactive layer, not a fork of the handbook

## Preview locally

From the repository root, any static file server works. With Python (already used by this repository's tooling):

```sh
python -m http.server 8000
```

Then open <http://localhost:8000/site/>.

The pages use ES modules, so open them through a local server rather than `file://`.

## Structure

```
site/
├── index.html              # landing page
├── capstone-01/index.html  # the Capstone 01 experience
├── styles/main.css         # design system, layout, responsive rules
└── js/
    ├── app.js              # stages, navigation, progress, rendering
    ├── capstone.js         # stage/capability/reference data (sources noted inline)
    ├── storage.js          # versioned localStorage wrapper
    └── markdown-export.js  # DECISIONS.md generation, download, copy
```

Reference data in `js/capstone.js` mirrors `capstones/01-agentic-system/reference/DECISIONS.md`; a Python stdlib test (`scripts/tests/test_site.py`) keeps the two from drifting.

## Not configured

Hosting. No GitHub Pages, no deployment workflow — publishing is a separate decision.
