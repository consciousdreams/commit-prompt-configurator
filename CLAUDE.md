# CLAUDE.md — commit-prompt-configurator

## What we're building

An IntelliJ IDEA plugin called **"Commit Prompt Configurator"** that adds a toolbar button to read and edit the AI Assistant commit message generation prompt (`com.intellij.ml.llm` plugin) — without navigating through Settings.

The prompt is saved globally and persists across IDE upgrades.

Repository: `consciousdreams/commit-prompt-configurator`

## Project structure

```
src/main/java/it/consciousdreams/
    SetCommitPromptAction.java          # toolbar action — dialog + orchestration
    WorkspacePrompt.java                # workspace.xml read/write logic
    GlobalPromptStorage.java            # app-level storage (global prompt, version-independent file)
src/main/resources/META-INF/
    plugin.xml                          # action and service registration
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
version = 1.0.0
```

**build.gradle.kts**
- Java toolchain: **21**
- `intellijIdea("2026.1")` — not `intellijIdeaCommunity`
- No compile-time dependency on `com.intellij.ml.llm`

**plugin.xml**
- ID: `it.consciousdreams.commit-prompt-configurator`
- `<depends>`: `com.intellij.modules.platform` only
- Registers: `applicationService` (`GlobalPromptStorage`)

## Technical approach: direct workspace.xml write

The plugin reads and writes **directly** to `.idea/workspace.xml`. It does not use `AISystemLibraryPromptService` — see below for why reflection on that service is not viable.

`AIAssistantCustomInstructionsStorage` (in `intellij.ml.llm.core.jar`) is annotated as:

```kotlin
@Service(Service.Level.PROJECT)
@State(name = "AIAssistantCustomInstructionsStorage", storages = [Storage("\$WORKSPACE_FILE\$")])
```

All custom prompts are therefore stored in `.idea/workspace.xml` with this structure:

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

**How read/write works (`WorkspacePrompt`):**
- `project.getWorkspaceFile()` → the `VirtualFile` for `workspace.xml`
- `JDOMUtil.load(byte[])` → returns an `Element` (**not** `loadDocument`, which does not exist)
- `JDOMUtil.write(Element)` to serialize, then `setBinaryContent()` inside `WriteAction.run()`
- Writing via VFS fires `VirtualFileListener` events, causing `StorageVirtualFileTracker` to reload the component immediately — no manual invalidation needed

## Global prompt

The prompt is saved in two places:
1. `.idea/workspace.xml` of the current project (so AI Assistant picks it up immediately)
2. `GlobalPromptStorage` — `@Service(Level.APP)`, stored at `<JetBrains-root>/commit-prompt-configurator/prompt.txt` via plain file I/O (not `@State`/`@Storage`), so it survives IDE upgrades. Path example: `~/Library/Application Support/JetBrains/commit-prompt-configurator/prompt.txt`. Path is computed via `PathManager.getConfigDir().getParent()`.

**Read priority in the dialog:**
1. `GlobalPromptStorage` (`prompt.txt`) — re-read from disk on every button click
2. Current project's `workspace.xml` (fallback if `prompt.txt` is empty)
3. AI Assistant default prompt via reflection on `LLMCommitCustomizablePrompt.getDefaultPrompt()`

`workspace.xml` is updated only when the user explicitly saves via the dialog.

The only remaining reflection is for reading the default prompt: `LLMCommitCustomizablePrompt` lives in `ml-llm.jar`, reachable from the main plugin classloader.

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

Testing via `runIde` sandbox is not possible (AI Assistant is not bundled and requires a JetBrains subscription). Correct procedure:
1. `./gradlew buildPlugin`
2. **Settings → Plugins → Install Plugin from Disk** → `.zip` from `build/distributions/`
3. Click the toolbar button, verify the dialog opens with the current prompt
4. Edit and save, then verify in **Settings → Tools → AI Assistant → Prompt Library**
5. Verify `~/Library/Application Support/JetBrains/commit-prompt-configurator/prompt.txt` was created/updated

## Marketplace description

**Short:**
> Quickly set and customize the AI Assistant commit message generation prompt from a toolbar button.

**Full:**
> Commit Prompt Configurator gives you instant access to the AI Assistant's commit message generation prompt directly from the toolbar.
> Instead of navigating through Settings → Tools → AI Assistant → Prompt Library → Built-In Actions → Commit Message Generation every time, this plugin adds a dedicated action that opens an editor dialog pre-filled with the current prompt, lets you edit freely, and saves immediately to the AI Assistant settings.
>
> The prompt is saved globally and persists across IDE upgrades.
>
> Requirements: JetBrains AI Assistant must be installed and active.