# First-time upload to a new GitHub repository

Create an empty repository named `SPECTRA` in your GitHub account. It must be a separate new repository; do not reuse any existing project. Leave README/license/gitignore unchecked when creating, since they are already included.

From the project folder on a computer with Git installed:

```sh
git init -b main
git add .
git commit -m "feat: initial SPECTRA 0.1 Android diagnostics prototype"
git remote add origin https://github.com/lolokeksu09/SPECTRA.git
git push -u origin main
```

GitHub Actions automatically runs unit tests, compiles the APK and publishes a **development prerelease** to Releases for every successful main commit. The same APK is available under GitHub > SPECTRA > Actions > newest successful `Android APK` run > Artifacts > `SPECTRA-0.1-debug`.

After verifying the build and when you want a prerelease, run:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The workflow uploads the debug APK to Releases as a prerelease. This development package is debug signed, not production release signed.
