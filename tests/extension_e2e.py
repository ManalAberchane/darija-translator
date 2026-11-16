"""End-to-end test of the Chrome extension: loads it unpacked into Chromium and drives
the side panel against a running backend (http://localhost:8080, user tester/secret).

Usage: python tests/extension_e2e.py [screenshot-dir]
"""
import pathlib
import sys
import tempfile

from playwright.sync_api import expect, sync_playwright

EXTENSION = pathlib.Path(__file__).resolve().parent.parent / "chrome-extension"
SHOTS = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
SELECTED = "Where is the nearest pharmacy?"


def main():
    with sync_playwright() as p, tempfile.TemporaryDirectory() as profile:
        context = p.chromium.launch_persistent_context(
            profile,
            headless=False,
            args=[f"--disable-extensions-except={EXTENSION}", f"--load-extension={EXTENSION}"],
            viewport={"width": 420, "height": 900},
        )
        worker = context.service_workers[0] if context.service_workers else context.wait_for_event("serviceworker")
        extension_id = worker.url.split("/")[2]
        print("1. extension loaded, id", extension_id)

        for _ in range(20):
            behaviour = worker.evaluate("chrome.sidePanel.getPanelBehavior()")
            if behaviour["openPanelOnActionClick"]:
                break
            worker.evaluate("new Promise(r => setTimeout(r, 100))")
        options = worker.evaluate("chrome.sidePanel.getOptions({})")
        assert behaviour["openPanelOnActionClick"] is True, behaviour
        assert options["path"].endswith("sidepanel.html"), options
        print("2. side panel registered:", options["path"], "| opens on toolbar click")

        panel = context.new_page()
        panel.goto(f"chrome-extension://{extension_id}/sidepanel.html")
        expect(panel.locator("#settings")).to_be_visible()
        print("3. first run shows the settings form")

        panel.fill("#username", "tester")
        panel.fill("#password", "wrong")
        panel.click("#saveSettings")
        panel.click("#testConnection")
        expect(panel.locator("#settingsStatus")).to_have_text("Server reached, but the username or password is wrong")
        print("4. wrong password detected by Test connection")

        panel.fill("#password", "secret")
        panel.click("#saveSettings")
        panel.click("#testConnection")
        expect(panel.locator("#settingsStatus")).to_have_text("Connected and authenticated")
        print("5. correct credentials saved and verified")
        if SHOTS:
            panel.screenshot(path=str(SHOTS / "extension-settings.png"))

        page = context.new_page()
        page.set_content(f"<h1>Test article</h1><p id='p'>{SELECTED}</p>")
        page.evaluate("""() => { const r = document.createRange(); r.selectNodeContents(document.getElementById('p'));
                                  const s = getSelection(); s.removeAllRanges(); s.addRange(r); }""")
        selection = page.evaluate("getSelection().toString()")
        worker.evaluate("""([text]) => handleMenuClick({menuItemId: 'translate-to-darija', selectionText: text}, {windowId: -1})""",
                        [selection])
        expect(panel.locator("#sourceText")).to_have_value(SELECTED)
        expect(panel.locator("#resultText")).to_have_text(f"دارجة: {SELECTED}")
        print("6. context-menu selection transferred to the panel and translated:", panel.inner_text("#resultText"))

        panel.click("button[data-script=latin]")
        panel.fill("#sourceText", "See you tomorrow")
        panel.click("#translateButton")
        expect(panel.locator("#resultText")).to_have_text("latin: See you tomorrow")
        assert panel.get_attribute("#resultText", "dir") == "ltr"
        print("7. Latin-script option sent to the API")

        panel.evaluate("navigator.clipboard.writeText = async (text) => { window.copiedText = text; }")
        panel.click("#copyButton")
        expect(panel.locator("#status")).to_have_text("Translation copied")
        assert panel.evaluate("window.copiedText") == "latin: See you tomorrow"
        print("8. copy button puts the translation on the clipboard")

        expect(panel.locator("#history li")).to_have_count(2)
        panel.locator("#history li").nth(1).click()
        expect(panel.locator("#resultText")).to_have_text(f"دارجة: {SELECTED}")
        print("9. history keeps both translations and restores one on click")

        panel.fill("#sourceText", "")
        panel.click("#translateButton")
        expect(panel.locator("#status")).to_have_text("Enter or select some text first")
        print("10. empty input rejected in the UI")

        panel.click("button[data-script=arabic]")
        panel.fill("#sourceText", "Good evening, my friend")
        panel.click("#translateButton")
        expect(panel.locator("#resultText")).to_have_text("دارجة: Good evening, my friend")
        if SHOTS:
            panel.screenshot(path=str(SHOTS / "extension-panel.png"), full_page=True)

        if panel.locator("#settings").is_hidden():
            panel.click("#settingsToggle")
        panel.fill("#serverUrl", "http://localhost:8099")
        panel.click("#saveSettings")
        panel.click("#translateButton")
        expect(panel.locator("#status")).to_contain_text("Cannot reach the server")
        print("11. unreachable backend reported clearly")

        context.close()
        print("ALL EXTENSION CHECKS PASSED")


if __name__ == "__main__":
    main()
