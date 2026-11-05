# Changes

## 0.2-experimental

- Add native ALAC decoding and direct stereo PCM output at 44.1 kHz / 16-bit, with corresponding honest discovery and /info capability declarations.
- Apply AirPlay volume/mute to this app’s AudioTrack only; TV system volume remains unchanged.
- Keep audio/listening active when only the video stream ends.
- Retry transient decoder input starvation for up to 250 ms while draining both outputs; retain bounded failure behavior.
- Increase bounded startup audio headroom; carry source dimensions with each queued frame and avoid oversized square decoder allocation.
- Auto request 1080p/30 when the display and AVC decoder report support; retain 720p and cap this version at 1080p.
- Preserve local failure codes/counters in Diagnostics; provide app-tag-only Mac log capture. No PINs, keys, sender names, screen contents or raw packet dumps are logged there.
- Establish fullscreen geometry before decoding; release idle audio output and guard against restarting a still-releasing codec worker.
- Remove the old protected-content warning from the receiver UI. It was text, not a playback filter; DRM handling is unchanged.

All new playback/quality behavior still requires physical device validation. No private device diagnostics or sender OS-version report is included in this changelog.
