# Punch the Clown — build tracks

**Standard workflow:** iterate with a sideloadable Development APK. Request a Google Play Internal Testing AAB only for milestone or Play services validation.

## Development APK (fast iteration)

GitHub Actions workflow: **Build Punch the Clown APK**

- Every pull request and push to `main` builds a **DEV APK**, not a Play Store update.
- Debug build application ID: `com.rabidstudios.punchtheclown.dev`
- Launcher label: **Punch the Clown DEV**.
- Version name: regular project version plus `-dev`. Uses the repository's current CI debug signing key.
- Artwork, layouts, game mechanics, local stats and offline play can be tested without changing your Play Store installation.
- Google Play Games login, online leaderboards/achievements and real Google Play Billing are intentionally **disabled** in DEV. Use **Google Play Internal Testing** for those features.
- Rewarded ads in DEV use Google's rewarded **test ad unit**. Do not interpret development ad impressions as real revenue.

From **Actions → Build Punch the Clown APK**, download the `*-DEV-sideload-APK` artifact; unzip it to obtain `app-debug.apk`.

With Android Debug Bridge (ADB), install or update development builds using:

```bat
adb devices
adb install -r app-debug.apk
```

The Google Play release remains installed alongside `Punch the Clown DEV`, with separate scores, preferences and purchases. The official Play Store app keeps package ID `com.rabidstudios.punchtheclown`.

## Major milestone / Google Play Internal Testing

1. After reviewing DEV changes, open GitHub **Actions → Build Punch the Clown APK → Run workflow**.
2. Select the intended branch/commit and check **Also build the Google Play Internal Testing AAB (major milestone)**.
3. Run the workflow and download the separate `*-play-internal-aab` artifact.
4. Extract `app-playInternal.aab`, upload to **Google Play Console → Internal testing**, add release notes, and roll out.
5. Update through the Play Store. Do not try to overwrite the Play Store app with the DEV APK.

The AAB continues to use the original application ID `com.rabidstudios.punchtheclown` and the existing Google Play upload signing flow. Check the incremented versionCode before each Play upload. **Do not reuse the dev package ID for Play uploads.**

**Note:** GitHub Actions' manual workflow selector becomes available from the repository's default branch after these workflow changes are merged. The development APK CI can still validate the pull request.

## Guardrails

- Approved game artwork and the existing Leaderboards balloon remain unchanged.
- Work-in-progress UI changes can be validated in the DEV app, but purchase/leaderboard/achievement functionality must be tested from a Google Play Internal Testing install before release.
- The debug CI keystore is for testing only, not for public production signing.
