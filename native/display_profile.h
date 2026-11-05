// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include "raop.h"
static inline int cinema_display_profile(raop_t *r,int width,int height) {
    if(!((width==1280 && height==720)||(width==1920 && height==1080))) return -1;
    raop_set_plist(r,"width",width);raop_set_plist(r,"height",height);
    raop_set_plist(r,"maxFPS",30);raop_set_plist(r,"refreshRate",30);return 0;
}
