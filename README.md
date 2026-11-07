# Commit Prompt Configurator

![Build](https://github.com/consciousdreams/commit-prompt-configurator/workflows/Build/badge.svg)

Quickly customize commit message generation for Air and AI Assistant from a toolbar button.

The button is available when either integration is active. It opens an editor pre-filled with your current prompt, so you can edit and save it in seconds.

The prompt is applied to the current project and saved globally so it persists across IDE upgrades.

## Usage

Click the **Set Commit Prompt** button in the toolbar. Edit the prompt in the dialog and click **Save**.

Use **Reset to Default** to load the default prompt into the editor without saving it. When both integrations are active, Air's default takes precedence.

On save, the prompt is written to the current project's settings for each active integration and to a global copy at `<JetBrains-root>/commit-prompt-configurator/prompt.txt`. Air stores its project prompt in `.idea/vcs.xml`; AI Assistant uses `.idea/workspace.xml`. When the dialog opens, it loads the global copy first, then Air's project prompt if Air is active, and then AI Assistant's project prompt.

## Installation

- **From disk:**

  Download the [latest release](https://github.com/consciousdreams/commit-prompt-configurator/releases/latest) and install it via
  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- **From Marketplace:**

  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search **Commit Prompt Configurator** > <kbd>Install</kbd>
