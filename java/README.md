# Processing Java Mode

This the Java Mode in Processing. It compiles your sketches and runs them. It is the primary mode of Processing.

## Folders
- `application` assets for exporting applications within the mode
- `generated` generated antlr code for the mode, should be moved to a proper `antlr` plugin within gradle
- `libraries` libraries that are available within the mode
- `lsp` gradle build system for the language server protocol, in the future we should decouple the lsp from the java mode and pde and move all relevant code here. For now it can be found in `src/.../lsp`
- `mode` legacy files for `Ant`
- `preprocessor` the preprocessor for the mode, same deal as with the lsp, although the decoupling has mostly been done
- `src` the main source code for the mode
- `test` tests for the mode
- `theme` assets for the mode, related to autocomplete and syntax highlighting

## Future plans
- Decouple the `lsp` and `preprocessor` from the mode and move them to their own repositories
- Move the `antlr` code to a proper plugin within gradle
- Create a gradle plugin to convert `.pde` file to `.java` files
- Create a gradle based version of Java mode.