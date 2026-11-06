// Applies the rules downloaded by background.js on top of hide.css.
const style = document.createElement("style");
style.id = "anti-reelshorts-remote";

browser.storage.local.get("remoteCss").then(({ remoteCss }) => {
  style.textContent = remoteCss || "";
  (document.head || document.documentElement).appendChild(style);
});

browser.storage.onChanged.addListener((changes) => {
  if (changes.remoteCss) style.textContent = changes.remoteCss.newValue || "";
});
