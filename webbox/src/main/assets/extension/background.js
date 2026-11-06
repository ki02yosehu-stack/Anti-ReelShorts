// Downloads extra hide rules from the repository so a broken rule can be fixed
// without reinstalling the app. content.js applies them.
const RULES_URL =
  "https://raw.githubusercontent.com/ki02yosehu-stack/Anti-ReelShorts/main/webbox/rules/remote.css";
const UPDATE_INTERVAL_MS = 6 * 60 * 60 * 1000;

async function updateRules() {
  try {
    const response = await fetch(RULES_URL, { cache: "no-store" });
    if (response.ok) {
      await browser.storage.local.set({ remoteCss: await response.text() });
    }
  } catch (e) {
    // Offline: keep the last rules we downloaded.
  }
}

updateRules();
setInterval(updateRules, UPDATE_INTERVAL_MS);
