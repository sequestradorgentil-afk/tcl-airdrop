// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <cstddef>
#include <cstdint>
struct AVCodecContext;
struct AVPacket;
struct AVFrame;
class AlacDecoder {
    AVCodecContext *context=nullptr;
    AVPacket *packet=nullptr;
    AVFrame *frame=nullptr;
public:
    AlacDecoder();
    ~AlacDecoder();
    AlacDecoder(const AlacDecoder&)=delete;
    AlacDecoder& operator=(const AlacDecoder&)=delete;
    // AirPlay ALAC magic cookie: 352 samples, stereo, signed 16 bit, 44100 Hz.
    bool decode(const uint8_t *data,size_t size,uint8_t *pcm,size_t capacity,size_t &written);
    void flush();
};
