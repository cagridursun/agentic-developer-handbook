// Landing page: a quiet launch video. It autoplays muted and loops, but the
// visitor stays in control: a visible Pause/Play button, no autoplay when the
// system asks for reduced motion, and the poster frame if the file is missing.
// Without this script the video keeps its native controls.

const video = document.getElementById("launch-video");
const toggle = document.getElementById("video-toggle");

function render() {
  const playing = !video.paused;
  toggle.textContent = playing ? "Pause" : "Play";
  toggle.setAttribute("aria-pressed", String(!playing));
  toggle.setAttribute("aria-label", playing ? "Pause the launch video" : "Play the launch video");
}

function flip() {
  if (video.paused) {
    video.play().catch(() => {});
  } else {
    video.pause();
  }
}

if (video && toggle) {
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  // The custom control replaces the native bar for a cleaner frame.
  video.removeAttribute("controls");
  toggle.hidden = false;

  if (reducedMotion.matches) {
    video.removeAttribute("autoplay");
    video.pause();
  }

  video.addEventListener("play", render);
  video.addEventListener("pause", render);
  video.addEventListener("click", flip);
  toggle.addEventListener("click", flip);

  // H.264 first, VP9 WebM for browsers without H.264. Only when the last
  // source fails too is the video unavailable: keep the poster, drop the
  // control.
  const sources = video.querySelectorAll("source");
  const last = sources[sources.length - 1] || video;
  last.addEventListener("error", () => {
    toggle.hidden = true;
    video.classList.add("is-unavailable");
  });

  render();
}
