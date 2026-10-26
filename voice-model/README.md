# Bundled Russian Vosk model

The five `.part01`–`.part05` files are consecutive binary parts of the original `vosk-model-small-ru-0.22.zip`. They stay below the connected GitHub upload request limit. They are source assets, not files users install.

`assembleVoskModel` concatenates them in numeric order into `build/generated/voice/vosk-model-small-ru-0.22.zip`. `verifyVoskModel` requires 46,236,750 bytes and SHA-256 `961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4` before resources are packaged. The final mod JAR contains the original complete archive at `/assets/kingdomnpc/voice/vosk-model-small-ru-0.22.zip`.

License and notices: `src/main/resources/assets/kingdomnpc/voice/LICENSE-vosk-model.txt` and `NOTICE-vosk-model.txt`.
