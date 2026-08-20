# CLAUDE.md — commit-prompt-configurator

## Cosa stiamo costruendo

Un plugin IntelliJ IDEA chiamato **"Commit Prompt Configurator"** che aggiunge un bottone toolbar per leggere e modificare il prompt di generazione commit dell'AI Assistant (plugin `com.intellij.ml.llm`) — senza dover navigare in Settings.

Repository: `consciousdreams/commit-prompt-configurator`

## Struttura del progetto

```
src/main/java/it/consciousdreams/
    SetCommitPromptAction.java          # unica classe — tutta la logica
src/main/resources/META-INF/
    plugin.xml                          # registrazione action e metadati plugin
    pluginIcon.svg                      # 40x40, Settings → Plugins e Marketplace
src/main/resources/icons/
    SetCommitPromptAction.svg           # 16x16, bottone toolbar
build.gradle.kts                        # dipendenze e configurazione build
gradle.properties                       # group, version
settings.gradle.kts                     # nome progetto, repository plugin
```

Non ci sono file Kotlin né test: il template è stato ripulito completamente.

## Comandi

```bash
./gradlew buildPlugin      # build zip in build/distributions/
./gradlew compileJava      # solo compilazione
```

Per installare: **Settings → Plugins → Install Plugin from Disk**, seleziona il `.zip`.

## Configurazione build

**gradle.properties**
```properties
group = it.consciousdreams
version = 1.0.0
```

**build.gradle.kts**
- Java toolchain: **21**
- `intellijIdea("2026.1")` — non `intellijIdeaCommunity`
- Nessuna dipendenza su `com.intellij.ml.llm`

**plugin.xml**
- ID: `it.consciousdreams.commit-prompt-configurator`
- `<depends>`: solo `com.intellij.modules.platform`

## Approccio tecnico: scrittura diretta su workspace.xml

Il plugin legge e scrive **direttamente** il `.idea/workspace.xml` del progetto. Non usa il service `AISystemLibraryPromptService` — vedi sotto perché la reflection su quel service non è praticabile.

`AIAssistantCustomInstructionsStorage` (in `intellij.ml.llm.core.jar`) è annotato così:

```kotlin
@Service(Service.Level.PROJECT)
@State(name = "AIAssistantCustomInstructionsStorage", storages = [Storage("\$WORKSPACE_FILE\$")])
```

Quindi tutti i prompt custom finiscono in `.idea/workspace.xml` con questa struttura:

```xml
<component name="AIAssistantCustomInstructionsStorage">
  <option name="instructions">
    <map>
      <entry key="AIAssistant.VCS.GenerateCommitMessage">
        <value>
          <AIAssistantStoredInstruction>
            <option name="actionId" value="AIAssistant.VCS.GenerateCommitMessage" />
            <option name="content" value="...testo del prompt..." />
          </AIAssistantStoredInstruction>
        </value>
      </entry>
    </map>
  </option>
</component>
```

**Costanti chiave:**
- Prompt ID: `AIAssistant.VCS.GenerateCommitMessage` (valore di `LLMCommitCustomizablePrompt.PROMPT_ID`)
- Nome componente: `AIAssistantCustomInstructionsStorage`

**Come si legge/scrive:**
- `project.getWorkspaceFile()` → il `VirtualFile` di `workspace.xml`
- `JDOMUtil.load(byte[])` → ritorna un `Element` (**non** `loadDocument`, che non esiste)
- `JDOMUtil.write(Element)` per serializzare, poi `setBinaryContent()` dentro una `runWriteAction`
- Scrivere via VFS fa scattare i `VirtualFileListener`, quindi `StorageVirtualFileTracker` ricarica il componente subito — non serve invalidare nulla a mano

L'unica reflection rimasta è per leggere il prompt di default quando non c'è ancora nessun valore custom: `LLMCommitCustomizablePrompt.getDefaultPrompt()`. Quella classe è in `ml-llm.jar`, raggiungibile dal classloader principale del plugin.

### Perché NON usare AISystemLibraryPromptService via reflection

Tentato e abbandonato. `AISystemLibraryPromptService` sta in `lib/modules/intellij.ml.llm.chat.jar`, che in IDEA 2024+ è un **content module** con classloader separato. Il problema:

- `PluginManagerCore.getPlugin(PluginId.getId("com.intellij.ml.llm"))` ritorna il classloader principale, che vede solo `ml-llm.jar` → `ClassNotFoundException`
- Cercare i content module per nome (`intellij.ml.llm.chat`, `intellij.ml.llm.core`, ...) via `getPlugin()` ritorna sempre `null` — non sono registrati come PluginId
- `PluginManagerCore.getLoadedPlugins()` non include i content module descriptor
- Cercare il classloader via l'extension point `com.intellij.projectService` non funziona: il service è registrato con l'annotation `@Service` (light service), non via XML, quindi non compare nell'EP

Verificato su AI Assistant `262.9437.216` / IDEA 2026.2.

### Come ispezionare i JAR dell'AI Assistant

Utile quando l'API cambia. I JAR sono in:

```
~/Library/Application Support/JetBrains/IntelliJIdea<versione>/plugins/ml-llm/lib/
~/Library/Application Support/JetBrains/IntelliJIdea<versione>/plugins/ml-llm/lib/modules/
```

```bash
# trovare in quale jar sta una classe
jar tf <jar> | grep NomeClasse

# firme dei metodi
jar xf <jar> path/to/Class.class && javap -p path/to/Class.class

# annotation @State/@Service e valori delle costanti
javap -verbose path/to/Class.class | grep -A5 RuntimeVisibleAnnotations
```

## Test

Non è possibile testare via `runIde` sandbox (AI Assistant non è bundled e richiede abbonamento JetBrains). Procedura corretta:
1. `./gradlew buildPlugin`
2. **Settings → Plugins → Install Plugin from Disk** → `.zip` da `build/distributions/`
3. Clicca il bottone nel toolbar, verifica che il dialog si apra con il prompt corrente
4. Modifica e salva, poi verifica in **Settings → Tools → AI Assistant → Prompt Library**

## Descrizione plugin (Marketplace)

**Short:**
> Quickly set and customize the AI Assistant commit message generation prompt from a toolbar button.

**Full:**
> Commit Prompt Configurator gives you instant access to the AI Assistant's commit message generation prompt directly from the toolbar.
> Instead of navigating through Settings → Tools → AI Assistant → Prompt Library → Built-In Actions → Commit Message Generation every time, this plugin adds a dedicated action that opens an editor dialog pre-filled with the current prompt, lets you edit freely, and saves immediately to the AI Assistant settings.
>
> Requirements: JetBrains AI Assistant must be installed and active.