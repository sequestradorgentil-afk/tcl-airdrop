// SPDX-License-Identifier: GPL-3.0-or-later
#ifndef CINEMA_PACKET_LIMITS_H
#define CINEMA_PACKET_LIMITS_H
#include <stddef.h>
#include <stdint.h>
static inline int cinema_avc_config_valid(const uint8_t *p,size_t n) {
    if(!p || n<11) return 0;
    if(p[4]==0x68 && p[5]==0x76 && p[6]==0x63 && p[7]==0x31) return 0; // HEVC disabled
    size_t sps=((size_t)p[6]<<8)|p[7];
    if(!sps || sps>n-11) return 0;
    size_t pps=((size_t)p[sps+9]<<8)|p[sps+10];
    return pps && pps<=n-11-sps;
}
#endif
