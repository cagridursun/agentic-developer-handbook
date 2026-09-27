# Social preview

`social-preview.png` is the image uploaded under the repository's **Settings → General → Social preview**. GitHub shows it when someone shares a link to the repository. `card.html` is its source.

- Size: 1280 × 640, with all content inside the 80 px (40pt) safe border GitHub recommends.
- Palette: the dark launch theme from [site/styles/main.css](../../site/styles/main.css).
- Logo: the existing [logo-mark.png](../../site/assets/images/logo-mark.png), unchanged; only its outer glow is feathered into the background.

This folder is not part of the published site.

## Regenerate

Edit `card.html`, then render it with headless Chrome from the repository root:

```sh
chrome --headless=new --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1280,640 --screenshot=.github/social-preview/social-preview.png \
  .github/social-preview/card.html
```

On Windows, use the full path to `chrome.exe` (or `msedge.exe`) and an absolute `file:///` URL for the page. Upload the new PNG in the repository settings; committing it does not update GitHub's preview.
