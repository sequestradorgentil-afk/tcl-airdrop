// SPDX-License-Identifier: GPL-3.0-or-later
#ifndef CINEMA_CAPABILITIES_H
#define CINEMA_CAPABILITIES_H
#include <stdint.h>
#include "dnssd.h"
/* Published AirplayAudFmt values: openairplay/airplay2-receiver,
 * ap2/connections/audio.py at 6c343d3679ddb561c61566985acaaf587d0a3bd3.
 * These are audio-format bits, distinct from mDNS feature bits. */
#define CINEMA_AUDIO_FORMATS ((UINT64_C(1)<<11) | (UINT64_C(1)<<18) | (UINT64_C(1)<<22) | (UINT64_C(1)<<24))
static inline void cinema_capabilities(dnssd_t *d) {
    const int disabled[]={0,1,2,3,4,5,13,15,16,17,33,34,40,41,42,46,50};
    for(unsigned int i=0;i<sizeof(disabled)/sizeof(disabled[0]);i++)
        dnssd_set_airplay_features(d,disabled[i],0);
    dnssd_set_airplay_features(d,18,1); // PCM 44.1 kHz stereo 16-bit
    dnssd_set_airplay_features(d,19,1); // ALAC 44.1 kHz stereo 16-bit
    dnssd_set_airplay_features(d,20,1); // AAC; upstream feature bit, not format bit 20.
}

/* Strictly match the decoder/output paths actually built into this receiver. */
static inline int cinema_audio_format_valid(unsigned char ct,unsigned short spf,uint64_t format) {
    uint64_t expected=ct==1?(UINT64_C(1)<<11):ct==2?(UINT64_C(1)<<18):
                      ct==4?(UINT64_C(1)<<22):ct==8?(UINT64_C(1)<<24):0;
    if(!expected || (format && format!=expected)) return 0;
    return ct==1 ? spf<=4096 : spf==(ct==2?352:ct==4?1024:480);
}

#endif
