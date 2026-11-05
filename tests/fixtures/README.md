Synthetic, non-private codec fixture: 352 stereo signed 16-bit samples at 44100 Hz.
For sample i, left=(i*257 mod 65536)-32768, right=32767-(i*131 mod 65536).
ALAC packet generated from alac.pcm with FFmpeg's reference ALAC encoder and
extracted without re-encoding from the generated M4A. The decoder test requires
bit-exact recovery of the original PCM. This is a codec unit test, not evidence
of real AirPlay mirroring, Apple Music playback, or TV audio synchronization.
