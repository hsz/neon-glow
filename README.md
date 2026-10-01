# IDE Synthwave

A SynthWave-inspired theme for JetBrains IDEs. Requires IntelliJ Platform 2025.3 or newer.

Choose **SynthWave '84** under **Settings | Appearance & Behavior | Appearance**.

## Development

Use JDK 21 or newer. Gradle compiles the plugin with a Java 21 toolchain.

```shell
./gradlew test
./gradlew buildPlugin
./gradlew runIde
```

The plugin is licensed under Apache 2.0. See [LICENSE](LICENSE).
The palette is based on Robb Owen's [SynthWave '84](https://github.com/robb0wen/synthwave-vscode),
licensed under MIT. See [LICENSE.upstream](LICENSE.upstream).