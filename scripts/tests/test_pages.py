"""Checks that the site works as a GitHub Pages project site.

The Pages artifact is only the contents of site/, served under
https://cagridursun.github.io/agentic-developer-handbook/ . Locally the same
files are served under http://localhost:8000/site/ . Both work only if:

- links between site pages are relative and stay inside site/,
- nothing assumes the domain root ("/styles/...", "/capstone-01/"),
- canonical repository content is linked on GitHub, not via ../../ paths,
- nothing is loaded from the network at runtime.

Standard-library unittest only — no browser, no Node, no network.

Run from the repository root:

    python -m unittest discover -s scripts/tests
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SITE = REPO_ROOT / "site"
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "deploy-pages.yml"
REPOSITORY_URL = "https://github.com/cagridursun/agentic-developer-handbook"
PAGES_URL = "https://cagridursun.github.io/agentic-developer-handbook/"

ATTRIBUTE_URL = re.compile(r'(?:href|src)="([^"]+)"')
GITHUB_LINK = re.compile(re.escape(REPOSITORY_URL) + r'/(?:blob|tree)/main/([^"#\s]*)')


def read(path):
    return path.read_text(encoding="utf-8")


def site_pages():
    return sorted(SITE.rglob("*.html"))


def site_scripts():
    return sorted((SITE / "js").glob("*.js"))


class SiteEntryTest(unittest.TestCase):

    def test_entrypoint_exists_and_links_both_experiences(self):
        landing = read(SITE / "index.html")
        self.assertIn('href="capstone-01/index.html"', landing)
        self.assertIn('href="readiness-assessment/index.html"', landing)
        self.assertTrue((SITE / "capstone-01" / "index.html").is_file())
        self.assertTrue((SITE / "readiness-assessment" / "index.html").is_file())

    def test_landing_points_to_github_and_the_first_lab(self):
        landing = read(SITE / "index.html")
        self.assertIn(f'href="{REPOSITORY_URL}"', landing)
        self.assertIn(REPOSITORY_URL + "/blob/main/labs/01-model-call/README.md", landing)
        self.assertIn("You probably don't need all of these.", landing)

    def test_the_path_ends_with_a_deployment_that_is_not_a_production_platform(self):
        landing = re.sub(r"\s+", " ", read(SITE / "index.html"))
        path = landing.split('class="path"')[1].split("</ol>")[0]
        self.assertIn(f'href="{REPOSITORY_URL}/blob/main/labs/08-mcp/README.md">MCP</a>', path)
        self.assertIn(f'href="{REPOSITORY_URL}/blob/main/labs/09-evaluation/README.md">Evaluation</a>', path)
        self.assertIn(f'href="{REPOSITORY_URL}/blob/main/labs/10-observability/README.md">Observability</a>', path)
        self.assertIn(f'href="{REPOSITORY_URL}/blob/main/labs/11-security/README.md">Security</a>', path)
        self.assertEqual(12, path.count("<li>"))
        planned = landing.split('class="roadmap-note"')[1].split("</p>")[0]
        self.assertIn(f'href="{REPOSITORY_URL}/blob/main/labs/12-deployment/README.md">Deployment</a>', path)
        self.assertIn("not a production platform", planned)
        for implemented in ["MCP", "Evaluation", "Observability", "Security"]:
            self.assertNotIn(implemented, planned)


class ProjectPagesPathTest(unittest.TestCase):

    def test_relative_links_stay_inside_site_and_resolve(self):
        # Works both at /agentic-developer-handbook/ and at /site/ locally.
        for page in site_pages():
            for url in ATTRIBUTE_URL.findall(read(page)):
                if url.startswith(("https://", "http://", "mailto:", "#")):
                    continue
                target = (page.parent / url.split("#")[0]).resolve()
                self.assertTrue(target.is_relative_to(SITE.resolve()),
                                f"{page.relative_to(REPO_ROOT)}: {url} leaves site/")
                self.assertTrue(target.exists(), f"{page.relative_to(REPO_ROOT)}: {url} is missing")

    def test_no_repository_relative_links(self):
        # ../../docs, ../../capstones … break once only site/ is deployed.
        for path in [*site_pages(), *site_scripts()]:
            self.assertNotIn("../../", read(path), path.relative_to(REPO_ROOT))

    def test_no_root_relative_urls(self):
        for page in site_pages():
            for url in ATTRIBUTE_URL.findall(read(page)):
                self.assertFalse(url.startswith("/") and not url.startswith("//"),
                                 f"{page.relative_to(REPO_ROOT)}: root-relative {url}")
        for script in site_scripts():
            self.assertIsNone(re.search(r"""["'`]/(?:styles|js|capstone-01|readiness-assessment|index\.html)""",
                                        read(script)), script.name)

    def test_canonical_content_links_to_the_real_repository(self):
        seen = 0
        for path in [*site_pages(), *site_scripts()]:
            for repo_path in GITHUB_LINK.findall(read(path)):
                seen += 1
                self.assertTrue((REPO_ROOT / repo_path).exists(),
                                f"{path.relative_to(REPO_ROOT)} links missing {repo_path}")
        self.assertGreaterEqual(seen, 10)
        for target in [
            "capstones/01-agentic-system/README.md",
            "capstones/01-agentic-system/starter/DECISIONS.md",
            "docs/model-vs-decision-authority.md",
            "assessments/agentic-system-readiness/README.md",
            "assessments/agentic-system-readiness/ASSESSMENT.md",
        ]:
            self.assertTrue(any(f"{REPOSITORY_URL}/blob/main/{target}" in read(page)
                                for page in site_pages()), target)
        self.assertIn(f"{REPOSITORY_URL}/blob/main/assessments/agentic-system-readiness/examples/",
                      read(SITE / "js" / "readiness.js"))

    def test_public_metadata(self):
        expected = {
            SITE / "index.html": PAGES_URL,
            SITE / "capstone-01" / "index.html": PAGES_URL + "capstone-01/",
            SITE / "readiness-assessment" / "index.html": PAGES_URL + "readiness-assessment/",
        }
        for page, url in expected.items():
            head = read(page).split("<body")[0]
            self.assertRegex(head, r"<title>[^<]{10,}</title>")
            self.assertIn('<meta name="description"', head)
            self.assertIn(f'<link rel="canonical" href="{url}">', head)
            self.assertIn(f'<meta property="og:url" content="{url}">', head)
            for prop in ["og:title", "og:description", "og:type"]:
                self.assertIn(f'property="{prop}"', head, f"{page.name}: {prop}")

    def test_landing_social_preview_uses_the_launch_poster(self):
        head = read(SITE / "index.html").split("<body")[0]
        image = PAGES_URL + "assets/video/launch-poster.jpg"
        self.assertTrue((SITE / "assets" / "video" / "launch-poster.jpg").is_file())
        for tag in [
            f'<meta property="og:image" content="{image}">',
            '<meta property="og:image:alt" content="Agentic Developer Handbook launch preview">',
            '<meta name="twitter:card" content="summary_large_image">',
            '<meta name="twitter:title" content="Agentic Developer Handbook">',
            f'<meta name="twitter:image" content="{image}">',
            '<meta name="twitter:image:alt" content="Agentic Developer Handbook launch preview">',
        ]:
            self.assertIn(tag, head, tag)
        description = re.search(r'<meta property="og:description" content="([^"]+)">', head).group(1)
        self.assertIn(f'<meta name="twitter:description" content="{description}">', head)
        # The image URL must map onto a file the Pages artifact actually contains.
        for url in re.findall(r'content="(https://[^"]+\.(?:jpg|png))"', head):
            self.assertTrue(url.startswith(PAGES_URL), url)
            self.assertTrue((SITE / url[len(PAGES_URL):]).is_file(), url)


class PublicPresentationTest(unittest.TestCase):
    """Catches pages that load but render unstyled or disconnected."""

    STYLESHEET = re.compile(r'<link rel="stylesheet" href="([^"]+)">')
    # Classes that are semantic hooks only; their look comes from another class.
    UNSTYLED_HOOKS = {"status-table"}

    def test_every_page_loads_the_shared_stylesheet_relative_to_its_depth(self):
        for page in site_pages():
            hrefs = self.STYLESHEET.findall(read(page))
            self.assertEqual(1, len(hrefs), f"{page.relative_to(REPO_ROOT)}: stylesheet links {hrefs}")
            depth = len(page.relative_to(SITE).parts) - 1
            self.assertEqual("../" * depth + "styles/main.css", hrefs[0], page.relative_to(REPO_ROOT))

    def test_every_page_has_the_shared_site_navigation(self):
        for page in site_pages():
            text = read(page)
            prefix = "../" * (len(page.relative_to(SITE).parts) - 1)
            nav = text.split('<nav class="site-nav"')[1].split("</nav>")[0]
            self.assertIn(f'class="brand" href="{prefix}index.html"', text, page.name)
            self.assertIn(f'href="{prefix}readiness-assessment/index.html"', nav, page.name)
            self.assertIn(f'href="{prefix}capstone-01/index.html"', nav, page.name)
            self.assertIn(f'href="{REPOSITORY_URL}"', nav, page.name)

    def test_html_classes_are_defined_in_the_stylesheet(self):
        # A class the CSS does not know renders as plain, unstyled markup.
        defined = set(re.findall(r"\.([A-Za-z][\w-]*)", read(SITE / "styles" / "main.css")))
        for page in site_pages():
            used = {name for attr in re.findall(r'class="([^"]+)"', read(page)) for name in attr.split()}
            self.assertEqual(set(), used - defined - self.UNSTYLED_HOOKS, page.relative_to(REPO_ROOT))

    def test_landing_has_visible_calls_to_action(self):
        landing = read(SITE / "index.html")
        self.assertIn('class="hero"', landing)
        self.assertIn('class="button primary"', landing)
        self.assertIn("Start here", landing)
        for target in ["readiness-assessment/index.html", "capstone-01/index.html"]:
            self.assertRegex(landing, r'class="button [a-z]+" href="' + re.escape(target) + '"')


class PrivacyAndDependencyTest(unittest.TestCase):

    def test_no_remote_runtime_dependency(self):
        for page in site_pages():
            for tag in re.findall(r"<(?:script|link|img|iframe)\b[^>]*>", read(page)):
                if 'rel="canonical"' in tag:
                    continue
                self.assertNotIn("://", tag, f"{page.name}: {tag}")
        for script in site_scripts():
            text = read(script)
            for forbidden in ["fetch(", "XMLHttpRequest", "WebSocket", "sendBeacon",
                              "document.cookie", "import(", "gtag", "analytics"]:
                self.assertNotIn(forbidden, text, f"{script.name}: {forbidden}")
            for module in re.findall(r'from\s+"([^"]+)"', text):
                self.assertTrue(module.startswith("./"), f"{script.name}: {module}")

    def test_no_npm(self):
        for name in ["package.json", "package-lock.json", "node_modules"]:
            self.assertEqual([], [p for p in REPO_ROOT.rglob(name) if ".git" not in p.parts], name)

    def test_storage_keys_are_unchanged(self):
        self.assertIn('STORAGE_KEY = "adh.learning.v1.capstone01"', read(SITE / "js" / "storage.js"))
        self.assertIn('STORAGE_KEY = "adh.learning.v1.readinessAssessment"',
                      read(SITE / "js" / "readiness-storage.js"))


class DeployWorkflowTest(unittest.TestCase):

    def setUp(self):
        self.workflow = read(WORKFLOW)

    def test_deploys_the_contents_of_site(self):
        self.assertIn("path: ./site", self.workflow)
        for action in ["actions/checkout@", "actions/configure-pages@",
                       "actions/upload-pages-artifact@", "actions/deploy-pages@"]:
            self.assertIn(action, self.workflow)
        self.assertNotIn("gh-pages", self.workflow.split("\njobs:")[1])

    def test_triggers_permissions_environment_concurrency(self):
        for fragment in ["branches: [main]", "- site/**", "- .github/workflows/deploy-pages.yml",
                         "workflow_dispatch:", "contents: read", "pages: write",
                         "id-token: write", "name: github-pages",
                         "url: ${{ steps.deployment.outputs.page_url }}",
                         "group: pages"]:
            self.assertIn(fragment, self.workflow, fragment)
        self.assertNotIn("contents: write", self.workflow)


class LandingMediaTest(unittest.TestCase):
    """The launch video and logo: present, quiet, and served from site/."""

    def setUp(self):
        self.landing = read(SITE / "index.html")
        self.video = self.landing.split("<video")[1].split("</video>")[0]

    def test_video_autoplays_silently_inline_and_loops(self):
        for attribute in ["autoplay", "muted", "loop", "playsinline", "controls"]:
            self.assertRegex(self.video, rf"\b{attribute}\b", attribute)

    def test_video_sources_and_poster_exist_inside_site(self):
        sources = re.findall(r'<source src="([^"]+)" type="(video/[a-z0-9]+)"', self.video)
        self.assertEqual({"video/mp4", "video/webm"}, {kind for _, kind in sources})
        poster = re.search(r'poster="([^"]+)"', self.video).group(1)
        for relative in [poster, *[src for src, _ in sources]]:
            self.assertFalse(relative.startswith(("/", "http")), relative)
            self.assertTrue((SITE / relative).is_file(), relative)
        # Keep the page light: each video file stays small.
        for src, _ in sources:
            self.assertLess((SITE / src).stat().st_size, 4_000_000, src)

    def test_visitor_can_pause_and_reduced_motion_is_respected(self):
        self.assertIn('id="video-toggle"', self.landing)
        script = read(SITE / "js" / "landing.js")
        self.assertIn("prefers-reduced-motion", script)
        self.assertIn('removeAttribute("autoplay")', script)
        self.assertIn('src="js/landing.js"', self.landing)

    def test_logo_mark_and_favicon_on_every_page(self):
        for page in site_pages():
            prefix = "../" * (len(page.relative_to(SITE).parts) - 1)
            text = read(page)
            self.assertIn(f'src="{prefix}assets/images/logo-mark.png"', text, page.name)
            self.assertIn(f'<link rel="icon" type="image/png" href="{prefix}assets/images/favicon.png">', text, page.name)
        self.assertTrue((SITE / "assets" / "images" / "logo-mark.png").is_file())
        self.assertTrue((SITE / "assets" / "images" / "favicon.png").is_file())

    def test_palette_tokens_are_defined(self):
        css = read(SITE / "styles" / "main.css")
        for token, value in {
            "--graphite": "#0A0B0D", "--panel": "#0F1115", "--panel-raised": "#14161B",
            "--hairline": "#262A31", "--border": "#363B45", "--ink": "#ECEAE4",
            "--ink-muted": "#7B818D", "--ink-subtle": "#555B67", "--electric": "#2F6BFF",
            "--electric-soft": "#9DB8FF", "--signal": "#62E6F2", "--field": "#2B5CFF",
            "--paper": "#ECEAE4", "--ink-black": "#0B0C0E", "--code-string": "#A7D9A0",
            "--code-comment": "#5F6675",
        }.items():
            self.assertIn(f"{token}: {value};", css, token)


if __name__ == "__main__":
    unittest.main()
