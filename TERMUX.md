# Upload from Android using Termux

The zip is a source project, **not an APK**. It contains the automated GitHub APK build. Copy `SPECTRA-0.1.zip` to the Download folder on the phone, then run:

```sh
pkg install -y git gh unzip
termux-setup-storage
mkdir -p ~/spectra
unzip -q ~/storage/downloads/SPECTRA-0.1.zip -d ~/spectra
cd ~/spectra/SPECTRA-0.1
gh auth login
bash scripts/publish-from-termux.sh
```

The upload script creates **only a new private** `lolokeksu09/SPECTRA` repository and refuses to modify a repository with that name if it already exists. It exits if GitHub is not authenticated. It does not deploy to any VDS server or modify existing repositories. GitHub Actions produces the debug APK and publishes it in Releases on successful builds.
