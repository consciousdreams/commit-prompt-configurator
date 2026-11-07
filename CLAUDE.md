# CLAUDE.md — commit-prompt-configurator

## What we're building

An IntelliJ IDEA plugin called **"Commit Prompt Configurator"** that adds a toolbar button to read and edit the commit message generation prompt for Air or AI Assistant — without navigating through Settings.

The prompt is saved globally and persists across IDE upgrades.

Repository: `consciousdreams/commit-prompt-configurator`

## Project structure

```
src/main/java/it/consciousdreams/
    SetCommitPromptAction.java          # toolbar action — orchestration
    CommitPromptDialog.java             # DialogWrapper with text editor and Reset to Default button
    AiAssistantPrompt.java              # AI Assistant prompt in workspace.xml
    AirPrompt.java                      # Air commit prompt in .idea/vcs.xml
    GlobalPromptStorage.java            # app-level storage (global prompt, version-independent file)
src/main/resources/META-INF/
    plugin.xml                          # action and service registration
    withAiAssistant.xml                 # optional AI Assistant dependency descriptor
    pluginIcon.svg                      # 40x40, shown in Settings → Plugins and Marketplace
src/main/resources/icons/
    SetCommitPromptAction.svg           # 16x16, toolbar button icon
build.gradle.kts                        # dependencies and build config
gradle.properties                       # group, version
settings.gradle.kts                     # project name, plugin repository
```

## Commands

```bash
./gradlew buildPlugin      # build zip in build/distributions/
./gradlew compileJava      # compile only
```

To install: **Settings → Plugins → Install Plugin from Disk**, select the `.zip`.

## Build configuration

**gradle.properties**
```properties
group = it.consciousdreams
version = 1.1.0
```

**build.gradle.kts**
- Java toolchain: **21**
- `intellijIdea("2026.1")` — not `intellijIdeaCommunity`
- No compile-time dependency on `com.intellij.ml.llm`

**plugin.xml**
- ID: `it.consciousdreams.commit-prompt-configurator`
- Required dependency: `com.intellij.modules.platform`; optional dependency: `com.intellij.ml.llm`
- Registers: `applicationService` (`GlobalPromptStorage`)
- Action registered directly into `MainToolbarRight` and `MainToolBar` — no wrapping group, so it appears as a top-level item in the toolbar customisation menu

## Technical approach: direct project settings file writes

The plugin reads and writes AI Assistant's prompt directly in `.idea/workspace.xml`. It does not use `AISystemLibraryPromptService` — see below for why reflection on that service is not viable. Air 2026.3 stores its project commit message instruction as JSON in the `AgentCommitMessageProject` component of `.idea/vcs.xml`; `AirPrompt` reads and writes that component. Air's default instruction is read at runtime from the `instruction.default` property in the Air plugin's `lib/modules/intellij.air.shared.settings.jar`, located through the public `PluginManager` API. When Air is active, its project instruction and default take precedence over AI Assistant's.

The toolbar action is enabled only when either AI Assistant or Air is active. A plain `runIde` sandbox without either plugin shows the button disabled.

`AIAssistantCustomInstructionsStorage` (in `intellij.ml.llm.core.jar`) is annotated as:

```kotlin
@Service(Service.Level.PROJECT)
@State(name = "AIAssistantCustomInstructionsStorage", storages = [Storage("\$WORKSPACE_FILE\$")])
```

AI Assistant's custom prompts are stored in `.idea/workspace.xml` with this structure:

```xml
<component name="AIAssistantCustomInstructionsStorage">
  <option name="instructions">
    <map>
      <entry key="AIAssistant.VCS.GenerateCommitMessage">
        <value>
          <AIAssistantStoredInstruction>
            <option name="actionId" value="AIAssistant.VCS.GenerateCommitMessage" />
            <option name="content" value="...prompt text..." />
          </AIAssistantStoredInstruction>
        </value>
      </entry>
    </map>
  </option>
</component>
```

**Key constants:**
- Prompt ID: `AIAssistant.VCS.GenerateCommitMessage` (value of `LLMCommitCustomizablePrompt.PROMPT_ID`)
- Component name: `AIAssistantCustomInstructionsStorage`

**How read/write works (`AiAssistantPrompt`):**
- `project.getWorkspaceFile()` → the `VirtualFile` for `workspace.xml`
- `JDOMUtil.load(byte[])` → returns an `Element` (**not** `loadDocument`, which does not exist)
- `JDOMUtil.write(Element)` to serialize, then `setBinaryContent()` inside `WriteAction.run()`
- Writing via VFS fires `VirtualFileListener` events, causing `StorageVirtualFileTracker` to reload the component immediately — no manual invalidation needed

## Global prompt

The prompt is saved globally and in the settings of each active plugin:
1. `.idea/workspace.xml` of the current project when AI Assistant is active
2. `.idea/vcs.xml` of the current project when Air is active
3. `GlobalPromptStorage` — `@Service(Level.APP)`, stored at `<JetBrains-root>/commit-prompt-configurator/prompt.txt` via plain file I/O (not `@State`/`@Storage`), so it survives IDE upgrades. Path example: `~/Library/Application Support/JetBrains/commit-prompt-configurator/prompt.txt`. Path is computed via `PathManager.getConfigDir().getParent()`.

**Read priority in the dialog:**
1. `GlobalPromptStorage` (`prompt.txt`) — re-read from disk on every button click
2. Air's current project instruction in `.idea/vcs.xml` when Air is active
3. Current project's `workspace.xml` when AI Assistant is active (fallback if the other copies are empty)
4. Air's default instruction from its resource bundle when Air is active; otherwise AI Assistant's default via reflection on `LLMCommitCustomizablePrompt.getDefaultPrompt()`

`workspace.xml` is updated only when AI Assistant is active and the user explicitly saves via the dialog.

The AI Assistant integration uses `Class.forName` to detect the plugin and reflection to read its default prompt. `LLMCommitCustomizablePrompt` lives in `ml-llm.jar`, reachable from the main plugin classloader.

### Why NOT to use AISystemLibraryPromptService via reflection

Attempted and abandoned. `AISystemLibraryPromptService` lives in `lib/modules/intellij.ml.llm.chat.jar`, which in IDEA 2024+ is a **content module** with a separate classloader. The issues:

- `PluginManagerCore.getPlugin(PluginId.getId("com.intellij.ml.llm"))` returns the main classloader, which only sees `ml-llm.jar` → `ClassNotFoundException`
- Looking up content modules by name (`intellij.ml.llm.chat`, `intellij.ml.llm.core`, ...) via `getPlugin()` always returns `null` — they are not registered as PluginIds
- `PluginManagerCore.getLoadedPlugins()` does not include content module descriptors
- Looking up the classloader via the `com.intellij.projectService` extension point does not work: the service is registered via the `@Service` annotation (light service), not via XML, so it does not appear in the EP

Verified on AI Assistant `262.9437.216` / IDEA 2026.2.

### How to inspect AI Assistant JARs

Useful when the API changes. JARs are in:

```
~/Library/Application Support/JetBrains/IntelliJIdea<version>/plugins/ml-llm/lib/
~/Library/Application Support/JetBrains/IntelliJIdea<version>/plugins/ml-llm/lib/modules/
```

```bash
# find which jar contains a class
jar tf <jar> | grep ClassName

# method signatures
jar xf <jar> path/to/Class.class && javap -p path/to/Class.class

# @State/@Service annotations and constant values
javap -verbose path/to/Class.class | grep -A5 RuntimeVisibleAnnotations
```

## Testing

The plain `runIde` sandbox targets IDEA 2026.1 and has neither Air nor AI Assistant, so its toolbar button is disabled. For an end-to-end check:
1. `./gradlew buildPlugin`
2. Install the `.zip` from `build/distributions/` in IDEA 2026.3 with Air active; optionally enable AI Assistant too.
3. Click **Set Commit Prompt**, edit and save, then check **Settings → AI → Air → Commit Message**.
4. If AI Assistant is active, also check **Settings → Tools → AI Assistant → Prompt Library**.
5. Check that **Reset to Default** loads the expected default and that `~/Library/Application Support/JetBrains/commit-prompt-configurator/prompt.txt` contains the saved prompt.

## Marketplace description

**Short:**
> Quickly customize commit message generation for Air and AI Assistant from a toolbar button.

**Full:**
> Skip the long trip through Settings: edit the commit message prompt for JetBrains Air or AI Assistant with a dedicated button in the IDE toolbar.
>
> Review the current prompt, load the default, or save your own instructions. The saved prompt applies to the current project; a separate global copy preserves it across IDE upgrades.
