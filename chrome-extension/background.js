const MENU_ID = "translate-to-darija";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: MENU_ID,
    title: "Translate \"%s\" to Darija",
    contexts: ["selection"]
  });
});

chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true });

function handleMenuClick(info, tab) {
  if (info.menuItemId !== MENU_ID || !info.selectionText) {
    return;
  }
  // sidePanel.open() must run synchronously inside the user gesture, before any await.
  if (tab && tab.windowId !== undefined) {
    chrome.sidePanel.open({ windowId: tab.windowId }).catch((error) => console.warn("Side panel:", error.message));
  }
  chrome.storage.session.set({
    pendingTranslation: { text: info.selectionText.trim(), requestedAt: Date.now() }
  });
}

chrome.contextMenus.onClicked.addListener(handleMenuClick);
globalThis.handleMenuClick = handleMenuClick;
