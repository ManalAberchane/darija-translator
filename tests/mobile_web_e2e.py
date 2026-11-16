"""Drives the React Native app's web build in Chromium against a running backend
(http://localhost:8080, user tester/secret). Serve the web export on port 8081 first:
  npx expo export --platform web --output-dir dist && python -m http.server 8081 -d dist
"""
import pathlib
import sys

from playwright.sync_api import expect, sync_playwright

SHOTS = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={"width": 412, "height": 900}, device_scale_factor=2)
    page.goto("http://localhost:8081")

    expect(page.get_by_test_id("serverUrl")).to_be_visible()
    print("1. first launch opens Settings")
    page.get_by_test_id("serverUrl").fill("http://localhost:8080")
    page.get_by_test_id("username").fill("tester")
    page.get_by_test_id("password").fill("wrong")
    page.get_by_role("button", name="Test connection").click()
    expect(page.get_by_text("Server reached, but the username or password is wrong")).to_be_visible()
    print("2. wrong password detected (cross-origin request passed CORS)")
    page.get_by_test_id("password").fill("secret")
    page.get_by_role("button", name="Save").click()
    page.get_by_role("button", name="Test connection").click()
    expect(page.get_by_text("Connected and authenticated")).to_be_visible()
    print("3. settings saved and verified")

    page.get_by_role("tab", name="Translate").click()
    page.get_by_test_id("sourceText").fill("I will be there in ten minutes")
    page.get_by_test_id("translateButton").click()
    expect(page.get_by_test_id("translation")).to_have_text("دارجة: I will be there in ten minutes")
    print("4. translation displayed")
    if SHOTS:
        page.screenshot(path=str(SHOTS / "mobile-translate.png"))

    page.get_by_role("button", name="Latin (Arabizi)").click()
    page.get_by_test_id("sourceText").fill("Thank you")
    page.get_by_test_id("translateButton").click()
    expect(page.get_by_test_id("translation")).to_have_text("latin: Thank you")
    print("5. Latin script option works")

    page.get_by_test_id("sourceText").fill("")
    page.get_by_test_id("translateButton").click()
    expect(page.get_by_text("Enter some text first")).to_be_visible()
    print("6. empty input rejected")

    page.get_by_role("tab", name="History").click()
    expect(page.get_by_text("2 translations")).to_be_visible()
    if SHOTS:
        page.screenshot(path=str(SHOTS / "mobile-history.png"))
    page.get_by_text("I will be there in ten minutes").first.click()
    expect(page.get_by_test_id("sourceText")).to_have_value("I will be there in ten minutes")
    print("7. history lists both and reopens an entry")

    page.reload()
    page.get_by_role("tab", name="History").click()
    expect(page.get_by_text("2 translations")).to_be_visible()
    print("8. settings and history persist after restart")
    browser.close()
    print("ALL MOBILE APP CHECKS PASSED")
