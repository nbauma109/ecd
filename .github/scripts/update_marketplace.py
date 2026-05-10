#!/usr/bin/env python3
"""
Update the Eclipse Marketplace listing version after a release.

Usage:
    update_marketplace.py <version>

Environment variables:
    ECLIPSE_MARKETPLACE_USERNAME  Eclipse Foundation account username or e-mail
                                  (prompted interactively when absent)
    ECLIPSE_MARKETPLACE_PASSWORD  Eclipse Foundation account password
                                  (prompted interactively when absent)

The version lives inside the first "Solution" entry (a two-level Drupal IEF).
The script clicks through both Edit buttons to reach the version text field,
then saves the sub-form and the outer listing in two separate submits.
"""

import argparse
import getpass
import os
import sys

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError, sync_playwright

LISTING_URL = "https://marketplace.eclipse.org/content/ecd-fork-enhanced-class-decompiler"
EDIT_URL = f"{LISTING_URL}/edit"
SSO_HOST = "auth.eclipse.org"

# "Edit" button for the first solution entry (top-level IEF row).
# The --2 suffix is stable (Drupal uses the delta, not a random token, here).
SOLUTION_EDIT_BTN = "#field-resource-listing-release-0-edit--2"

# "Edit" button for the version entry nested inside the solution sub-form.
# Drupal generates a random token suffix on the id, so we match by substrings.
VERSION_EDIT_BTN = '[id*="resource-listing-release-0"][id*="version"][id*="edit"]'

# Version text input inside the expanded version sub-form.
VERSION_FIELD = 'input[name="field_resource_listing_release[0][subform][field_version][0][value]"]'


def login(page, username: str, password: str) -> None:
    print(f"Navigating to: {EDIT_URL}")
    page.goto(EDIT_URL, wait_until="networkidle")

    if SSO_HOST in page.url:
        print(f"Redirected to SSO login: {page.url}")
        page.fill("#username", username)
        page.fill("#password", password)
        page.click("#kc-login")
        page.wait_for_load_state("networkidle")

    if SSO_HOST in page.url or "/openid-connect/auth_" in page.url:
        raise RuntimeError(f"Login failed — still on login page: {page.url}")

    print(f"Authenticated. Current page: {page.url}")

    # The Keycloak callback drops us on the user profile rather than the original
    # destination, so navigate to the edit page explicitly after authentication.
    if page.url.rstrip("/") != EDIT_URL.rstrip("/"):
        print(f"Navigating to edit page: {EDIT_URL}")
        page.goto(EDIT_URL, wait_until="networkidle")


def _wait_for_ajax(page) -> None:
    page.wait_for_load_state("networkidle")
    # Drupal renders a throbber while AJAX is in flight; wait for it to disappear.
    try:
        page.wait_for_selector(".ajax-progress", state="hidden", timeout=10_000)
    except PlaywrightTimeoutError:
        pass  # throbber may not appear for fast responses


def _click_ajax_btn(page, selector: str, label: str) -> None:
    btn = page.locator(selector)
    if btn.count() == 0:
        raise RuntimeError(f"Could not find {label} button ({selector!r}).")
    print(f"Clicking {label} button…")
    btn.first.click()
    _wait_for_ajax(page)


def update_version(page, version: str) -> None:
    _click_ajax_btn(page, SOLUTION_EDIT_BTN, "solution Edit")
    _click_ajax_btn(page, VERSION_EDIT_BTN, "version Edit")

    field = page.locator(VERSION_FIELD)
    if field.count() == 0:
        raise RuntimeError(f"Version field not found ({VERSION_FIELD}).")

    print(f"Setting version to {version!r}")
    field.fill(version)

    # First submit collapses the open IEF sub-forms back into the parent form.
    _click_ajax_btn(page, 'input[name="op"][value="Update"]', "IEF Update")

    # Second submit saves the outer listing node.
    page.locator("#edit-moderation-state-published").click()
    page.wait_for_load_state("networkidle")

    print(f"Saved. Final URL: {page.url}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Update Eclipse Marketplace listing version"
    )
    parser.add_argument("version", help="New version string, e.g. 2026.3.10")
    args = parser.parse_args()

    username = os.environ.get("ECLIPSE_MARKETPLACE_USERNAME", "")
    if not username:
        username = input("Eclipse Marketplace username: ")

    password = os.environ.get("ECLIPSE_MARKETPLACE_PASSWORD", "")
    if not password:
        password = getpass.getpass("Eclipse Marketplace password: ")

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_context().new_page()

        try:
            login(page, username, password)
            update_version(page, args.version)
        except Exception as exc:
            print(f"Error: {exc}", file=sys.stderr)
            sys.exit(1)
        finally:
            browser.close()


if __name__ == "__main__":
    main()
