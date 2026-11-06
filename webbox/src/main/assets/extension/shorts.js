// Hides the box around each Short on YouTube (a shelf, or a single Short in a
// list of normal videos), which hide.css can't do without slow :has() rules.
// Checks at most a few times a second, however fast the page changes.

// Nearest box to hide around each kind of Shorts element.
const RULES = [
  ["ytm-reel-shelf-renderer", "ytm-rich-section-renderer, ytm-item-section-renderer"],
  ["ytm-shorts-lockup-view-model, ytm-shorts-lockup-view-model-v2", "ytm-rich-section-renderer, grid-shelf-view-model"],
  ['a[href^="/shorts/"]', "ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-rich-item-renderer"],
  [".pivot-shorts", "ytm-pivot-bar-item-renderer"],
];
const DONE = "antiReelshorts";

function sweep() {
  scheduled = false;
  for (const [what, box] of RULES) {
    for (const el of document.querySelectorAll(what)) {
      if (el.dataset[DONE]) continue;
      el.dataset[DONE] = "1";
      el.closest(box)?.style.setProperty("display", "none", "important");
    }
  }
}

let scheduled = false;
new MutationObserver(() => {
  if (scheduled) return;
  scheduled = true;
  setTimeout(sweep, 250);
}).observe(document.documentElement, { childList: true, subtree: true });
