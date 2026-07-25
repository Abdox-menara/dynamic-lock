# Get the APK for free with GitHub (no software to install)

This turns the source into a real, installable `app-debug.apk` using GitHub's free build
servers. You only need a free GitHub account — it even works from a phone browser.
A build takes about 3–5 minutes.

## Steps

1. **Create a free account** at https://github.com (skip if you have one).

2. **Create a new repository**
   - Click the **+** (top-right) → **New repository**.
   - Give it any name, e.g. `dynamic-lock`. Leave it Public or Private — either works.
   - Click **Create repository**.

3. **Upload the project files**
   - Unzip `DynamicLock.zip` on your device.
   - On the new repo page, click **uploading an existing file** (or **Add file → Upload files**).
   - Drag in **everything that is *inside* the `DynamicLock` folder** — so that
     `settings.gradle.kts` and the `.github` folder sit at the **top level** of the repo
     (not inside another `DynamicLock` sub-folder).
   - Click **Commit changes**.

   > Tip: on the GitHub website you can drag the whole set of files/folders at once.
   > The important part is that `settings.gradle.kts` ends up at the repository root.

4. **Wait for the build**
   - Go to the **Actions** tab of your repo.
   - You'll see a run called **Build APK** with a spinning icon. Wait for the green ✓.
   - If it's red, open the run and read the failed step — usually it means the files weren't
     at the repo root (see step 3).

5. **Download the APK**
   - Open the finished (green ✓) run.
   - Scroll to **Artifacts** at the bottom → click **DynamicLock-debug-apk** to download.
   - It downloads as a `.zip`; unzip it to get **`app-debug.apk`**.

6. **Install on your phone**
   - Copy `app-debug.apk` to your Android phone.
   - Open it. Android will ask to allow installing from this source — accept
     (Settings → "Install unknown apps" for your browser/file manager).
   - Because it's a *debug* build it's unsigned for the Play Store, but it installs and runs fine
     for personal use.

## Re-building after changes
Any time you change a file in the repo (or click **Run workflow** on the Actions tab under
"Build APK"), a fresh APK is built and appears as a new artifact.
