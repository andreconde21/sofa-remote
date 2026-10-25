# Sofa

A TV remote for your phone. No ads, no account, no tracking.

- **Any Android TV or Google TV**: Xiaomi Mi TV / Mi Box / TV Stick, Sony, TCL, Philips, Hisense, Chromecast with Google TV, Nvidia Shield…
- **Real typing.** When a text box gets focus on the TV, your phone keyboard opens with the TV's text in it. Type, swipe, autocorrect, paste, backspace: the TV follows exactly, accents included.
- **Every TV in the house.** Finds TVs on the Wi-Fi by itself, pair each once, switch with a tap. "Turn off every TV" for bedtime.
- D-pad or touchpad, long-press OK, volume with the phone's buttons, media keys, channel, input, and one-tap YouTube / Netflix / Prime Video / Disney+ / Max / Spotify / Plex / Twitch.

Download: **https://sofa.outsmartis.dev**

## How it works

It speaks the Android TV Remote protocol v2, the one Google's own TV remote uses, over your local network only:

- **Discovery**: mDNS `_androidtvremote2._tcp`.
- **Pairing** (TCP 6467, TLS): the phone presents a self-signed client certificate; the TV shows a 6-character hex code; the phone sends SHA-256(client modulus, exponent, server modulus, exponent, last 4 code digits). The first code byte is a checksum of that hash, so typos are caught locally. See `tv/Pairing.kt`.
- **Remote** (TCP 6466, TLS with the same certificate): varint-length-prefixed protobuf messages. Keys (`remote_key_inject`), app links, power / volume / current app state, pings. See `tv/RemoteSession.kt`.
- **Typing**: the TV announces a focused field in `remote_ime_key_inject` (app info + field status). `remote_ime_batch_edit` **inserts** at the cursor, it never replaces, and must carry the IME counter from the app info and the field counter from the latest field status (the TV bumps it after every edit and echoes the new text in `remote_ime_show_request`). So each phone edit becomes Backspaces for the changed tail plus one insert, sent one at a time and confirmed by the echo. See `tv/Typist.kt`.

The protobuf messages are hand-encoded (`tv/Proto.kt`) and the client certificate is built with a tiny DER encoder (`tv/Identity.kt`), so the protocol code has no dependencies and runs in plain JVM tests.

## Build

```
./gradlew testDebugUnitTest assembleDebug
```

Release: push a tag `vX.Y.Z`; GitHub Actions signs the APK and attaches it to the release.

`LiveTvDriver` (a skipped-by-default test) drives a real TV or the Google TV emulator from the JVM: set `SOFA_TV_DIR` and append commands to `$SOFA_TV_DIR/cmd.txt`. On the emulator, the remote port refuses connections from its own loopback, so use `adb emu redir add tcp:26466:6466` (and 26467:6467) instead of `adb forward`.

## Credits

Protocol knowledge from [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2), [louis49/androidtv-remote](https://github.com/louis49/androidtv-remote) and Google's [pairing protocol](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/). Not affiliated with Google or Xiaomi.

MIT licensed.
