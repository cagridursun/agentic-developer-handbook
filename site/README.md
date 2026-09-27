# Interactive learning site

A static, backend-free learning layer over the handbook, published at **<https://cagridursun.github.io/agentic-developer-handbook/>**. It covers two experiences that follow Labs 01–07:

- **Capstone 01 — Build a Small Agentic System**: build and compare. Compose already-taught concepts, then compare with one reference architecture.
- **Agentic System Readiness Assessment**: decide what architecture a problem deserves. Bring your own use case; a fixed order of questions, starting with ordinary software, derives candidate capabilities by transparent, deterministic rules — no model call, no score — and exports an `ASSESSMENT.md`.

Boundaries, on purpose:

- No framework, no build step, no npm — semantic HTML, modern CSS, small ES modules
- No backend, no accounts, no analytics — progress lives only in your browser's `localStorage`, one namespaced, versioned key per experience (`adh.learning.v1.capstone01`, `adh.learning.v1.readinessAssessment`). Resetting one never touches the other
- The canonical content stays in the repository: Markdown, Java code, `DECISIONS.md`, and `assessments/`. The site is an interactive layer, not a fork of the handbook

## Public site

<https://cagridursun.github.io/agentic-developer-handbook/>

The [Deploy site to GitHub Pages](../.github/workflows/deploy-pages.yml) workflow publishes the **contents** of `site/` on every push to `main` that changes `site/**` (or the workflow itself), and can be run manually. There is no build step and no `gh-pages` branch.

The published site contains only `site/`. Links to canonical content — labs, docs, `DECISIONS.md`, the assessment Markdown — therefore point to the GitHub repository, never to `../../` paths, and links between pages stay relative so the site works both under `/agentic-developer-handbook/` and locally under `/site/`. `scripts/tests/test_pages.py` checks both rules.

The repository's Markdown and Java remain canonical. The site is an interactive layer, not a second copy of the documentation.

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
├── index.html                       # landing page: both experiences
├── capstone-01/index.html           # the Capstone 01 experience
├── readiness-assessment/index.html  # the readiness assessment experience
├── styles/main.css                  # design system, layout, responsive rules
└── js/
    ├── app.js                       # Capstone 01: stages, navigation, progress, rendering
    ├── capstone.js                  # Capstone 01 data (sources noted inline)
    ├── storage.js                   # Capstone 01 versioned localStorage wrapper
    ├── markdown-export.js           # DECISIONS.md generation, download, copy
    ├── readiness-app.js             # assessment: stages, questions, result rendering
    ├── readiness.js                 # assessment data (mirrors assessments/…/README.md)
    ├── readiness-logic.js           # deterministic rules: answers → candidate statuses
    ├── readiness-storage.js         # assessment versioned localStorage wrapper
    └── readiness-export.js          # ASSESSMENT.md generation, download, copy
```

Reference data in `js/capstone.js` mirrors `capstones/01-agentic-system/reference/DECISIONS.md`, and the assessment questions in `js/readiness.js` mirror `assessments/agentic-system-readiness/README.md`; Python stdlib tests (`scripts/tests/test_site.py`, `scripts/tests/test_assessment.py`) keep them from drifting.

The assessment's rules live in `js/readiness-logic.js` and are printed in [the assessment README](../assessments/agentic-system-readiness/README.md#how-the-summary-is-derived). They produce candidates to consider — never a score, and never "the correct architecture".

## Not configured

A custom domain and a social preview image. The site has no analytics, no cookies, and no remote calls; answers never leave the browser.
