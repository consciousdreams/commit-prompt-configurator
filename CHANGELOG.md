<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# commit-prompt-configurator Changelog

## [Unreleased]

### Added

- Support for Air commit message prompts alongside AI Assistant
- "Reset to Default" loads Air's default prompt when Air is active

### Fixed

- The toolbar button is disabled when neither AI Assistant nor Air is active
- "Reset to Default" is disabled when the default prompt is unavailable

## [1.0.2] - 2026-08-21

### Added

- "Reset to Default" button in the dialog restores the AI Assistant default prompt without saving

### Changed

- Dialog now uses a dedicated editor (monospaced font, scrollable) instead of the plain multiline input
- Action registered directly on the toolbar (no intermediate group in the customisation menu)

## [1.0.1] - 2026-08-20

### Added

- Toolbar action "Set AI Commit Prompt" opens a dialog pre-filled with the current prompt — edit and save in one click
- The prompt is saved globally and persists across IDE upgrades
- Saving via the dialog updates the current project immediately; AI Assistant picks it up without restarting

[Unreleased]: https://github.com/consciousdreams/commit-prompt-configurator/compare/1.0.2...HEAD
[1.0.2]: https://github.com/consciousdreams/commit-prompt-configurator/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/consciousdreams/commit-prompt-configurator/commits/1.0.1
