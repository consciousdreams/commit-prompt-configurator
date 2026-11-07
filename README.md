# Commit Prompt Configurator

![Build](https://github.com/consciousdreams/commit-prompt-configurator/workflows/Build/badge.svg)

Quickly set and customize the AI Assistant commit message generation prompt from a toolbar button.

Instead of navigating deep into Settings every time, this plugin adds a toolbar button that opens an editor pre-filled with the current prompt — edit and save in seconds.

The prompt is saved globally and persists across IDE upgrades.

The button works with Air as well as AI Assistant. When Air is active, it reads and updates Air's project commit message prompt in `.idea/vcs.xml`. When AI Assistant is active, it updates that plugin's prompt in `.idea/workspace.xml`.

## Usage

Click the **Set AI Commit Prompt** button in the toolbar. Edit the prompt in the dialog and click **Save**.

Use **Reset to Default** to restore the active plugin's original prompt. It fills the editor without saving, so you can still review or further edit before confirming.

The saved prompt is stored globally at `<JetBrains-root>/commit-prompt-configurator/prompt.txt` and written to the current project's settings for each active plugin on save. Air reads `.idea/vcs.xml`; AI Assistant reads `.idea/workspace.xml`. On every future use, the dialog first loads the global copy, then falls back to the active plugin's project settings.

## Installation

- **From disk:**

  Download the [latest release](https://github.com/consciousdreams/commit-prompt-configurator/releases/latest) and install it via
  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- **From Marketplace** *(once published)*:

  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search **Commit Prompt Configurator** > <kbd>Install</kbd>
