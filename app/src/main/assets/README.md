# Rive avatar

Drop your exported Rive file here as `avatar.riv` (exact name, lowercase) to
show it on the home screen.

- Design the avatar in the [Rive editor](https://rive.app) and export a
  `.riv` file (File → Export).
- Copy it to `app/src/main/assets/avatar.riv`.
- Rebuild and run — `HomeActivity` loads it automatically and autoplays its
  default artboard / state machine. No other artboard, animation, or state
  machine name is assumed, so any Rive file works out of the box.
- If this file is absent, the avatar view stays hidden and the rest of the
  launcher behaves as before.
- The avatar can also be hidden from Settings → "Show Rive avatar" without
  removing the file.
