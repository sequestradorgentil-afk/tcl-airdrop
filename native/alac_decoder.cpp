// SPDX-License-Identifier: GPL-3.0-or-later
#include "alac_decoder.h"
#include <cstring>
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/mem.h>
#include <libavutil/log.h>
}
AlacDecoder::AlacDecoder() {
    av_log_set_level(AV_LOG_QUIET);
    av_max_alloc(4*1024*1024);
    const AVCodec *codec=avcodec_find_decoder(AV_CODEC_ID_ALAC);
    if(!codec) return;
    context=avcodec_alloc_context3(codec);
    if(!context) return;
    static const uint8_t cookie[]={0,0,0,0x24,0x61,0x6c,0x61,0x63,0,0,0,0,
        0,0,1,0x60,0,0x10,0x28,0x0a,0x0e,2,0,0xff,0,0,0,0,0,0,0,0,0,0,0xac,0x44};
    context->extradata=(uint8_t*)av_mallocz(sizeof(cookie)+AV_INPUT_BUFFER_PADDING_SIZE);
    if(!context->extradata) return;
    memcpy(context->extradata,cookie,sizeof(cookie));
    context->extradata_size=sizeof(cookie);context->thread_count=1;context->max_samples=4096;
    if(avcodec_open2(context,codec,nullptr)<0) { avcodec_free_context(&context);return; }
    packet=av_packet_alloc();frame=av_frame_alloc();
}
AlacDecoder::~AlacDecoder() {
    av_frame_free(&frame);av_packet_free(&packet);avcodec_free_context(&context);
}
void AlacDecoder::flush() { if(context) avcodec_flush_buffers(context); }
bool AlacDecoder::decode(const uint8_t *data,size_t size,uint8_t *pcm,size_t capacity,size_t &written) {
    written=0;
    if(!context || !packet || !frame || !data || !size || size>65536 || !pcm) return false;
    av_packet_unref(packet);av_frame_unref(frame);
    if(av_new_packet(packet,(int)size)<0) return false;
    memcpy(packet->data,data,size); // av_new_packet includes zeroed bit-reader padding.
    int result=avcodec_send_packet(context,packet);av_packet_unref(packet);
    if(result<0 || avcodec_receive_frame(context,frame)<0) return false;
    if(frame->nb_samples<1 || frame->nb_samples>352 || frame->ch_layout.nb_channels!=2 ||
       frame->sample_rate!=44100 || capacity<(size_t)frame->nb_samples*4) return false;
    if(frame->format!=AV_SAMPLE_FMT_S16P && frame->format!=AV_SAMPLE_FMT_S16) return false;
    for(int i=0;i<frame->nb_samples;i++) for(int channel=0;channel<2;channel++) {
        const int16_t *samples=(const int16_t*)frame->extended_data[frame->format==AV_SAMPLE_FMT_S16P?channel:0];
        uint16_t value=(uint16_t)samples[frame->format==AV_SAMPLE_FMT_S16P?i:2*i+channel];
        pcm[written++]=(uint8_t)value;pcm[written++]=(uint8_t)(value>>8);
    }
    return true;
}
