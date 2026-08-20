# Commit Prompt Configurator

![Build](https://github.com/consciousdreams/commit-prompt-configurator/workflows/Build/badge.svg)

Quickly set and customize the AI Assistant commit message generation prompt from a toolbar button.

Instead of navigating through **Settings → Tools → AI Assistant → Prompt Library → Built-In Actions → Commit Message Generation** every time, this plugin adds a dedicated toolbar action that opens an editor dialog pre-filled with the current prompt, lets you edit freely, and saves immediately.

The prompt is saved globally — it automatically applies to every project you open.

**Requirements:** JetBrains AI Assistant must be installed and active.

## Usage

Click the **Set AI Commit Prompt** button in the toolbar. Edit the prompt in the dialog and click **OK** to save.

The saved prompt is stored globally and propagated to each project automatically on open.

## Installation

- **From disk:**

  Download the [latest release](https://github.com/consciousdreams/commit-prompt-configurator/releases/latest) and install it via
  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- **From Marketplace** *(once published)*:

  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search **Commit Prompt Configurator** > <kbd>Install</kbd>
