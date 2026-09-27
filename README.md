# PC Hardware Analyzer

A JavaFX desktop PC builder with component compatibility checks, saved builds in SQLite, and catalog synchronization from GitHub.

## Install on Windows

1. Open the repository's **Releases** page on GitHub.
2. Download the `.msi` installer from the latest release.
3. Run the MSI installer. It installs the app for the current Windows user and adds a Start menu entry and shortcut.

The MSI is created by the **Build Windows installer** GitHub Actions workflow. To publish a new installer, push a version tag such as `v1.0.0`; the workflow builds the MSI and attaches it to a GitHub Release. Running the workflow manually also creates an MSI artifact in the Actions run. Saved builds are kept under the current user's local application data folder.

## Run from source

Use JDK 21 and Maven, then run:

```shell
mvn javafx:run
```

## App icon

The JavaFX window uses `src/main/resources/icons/pc-hardware-analyzer.png`. The Windows installer uses `src/main/resources/icons/pc-hardware-analyzer.ico`. The editable vector source is `src/main/resources/icons/pc-hardware-analyzer.svg`.
