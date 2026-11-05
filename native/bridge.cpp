// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <atomic>
#include <string>
#include <ctime>
#include <cstring>
#include <mutex>
#include <memory>
#include "alac_decoder.h"
extern "C" {
#include "raop.h"
#include "capabilities.h"
#include "display_profile.h"
}

namespace {
JavaVM *vm;
jobject listener;
jmethodID packetMethod, eventMethod, sizeMethod, volumeMethod;
raop_t *server;
dnssd_t *discovery;
std::atomic<bool> running{false};
std::mutex senderMutex;
std::string senderId;
std::mutex audioMutex;
std::unique_ptr<AlacDecoder> alac;
struct Env {
    JNIEnv *env = nullptr;
    bool attached = false;
    Env() {
        if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
            attached = vm->AttachCurrentThread(&env, nullptr) == JNI_OK;
    }
    ~Env() { if (attached) vm->DetachCurrentThread(); }
};
void event(const char *text) {
    if (!running) return;
    Env e; if (!e.env) return;
    jstring s = e.env->NewStringUTF(text);
    if (s) { e.env->CallVoidMethod(listener, eventMethod, s); e.env->DeleteLocalRef(s); }
    if (e.env->ExceptionCheck()) e.env->ExceptionClear();
}
void packet(int kind, unsigned char *bytes, int length, uint64_t time) {
    if (!running || length <= 0 || length > 4*1024*1024) return;
    timespec ts{}; clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t now = int64_t(ts.tv_sec)*1000000000 + ts.tv_nsec;
    int64_t delta = int64_t(time) - int64_t(raop_ntp_get_local_time());
    // Both streams use the protocol's local NTP timeline plus a bounded 150ms budget.
    if (delta < -2000000000LL || delta > 2000000000LL) {
        event("Error: stream timestamp outside playback window"); return;
    }
    Env e; if (!e.env) return;
    jbyteArray data = e.env->NewByteArray(length);
    if (data) {
        e.env->SetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes));
        e.env->CallVoidMethod(listener, packetMethod, kind, data, jlong(now+delta+150000000));
        e.env->DeleteLocalRef(data);
    }
    if (e.env->ExceptionCheck()) e.env->ExceptionClear();
}
void audio(void *, raop_ntp_t *, audio_decode_struct *d) {
    if(d->ct==2) {
        std::lock_guard<std::mutex> lock(audioMutex);
        if(!alac) alac.reset(new AlacDecoder());
        uint8_t pcm[352*4];size_t length=0;
        if(!alac->decode(d->data,(size_t)d->data_len,pcm,sizeof(pcm),length)) {
            event("Error: ALAC packet could not be decoded (44.1 kHz / stereo / 16 bit required)");return;
        }
        packet(1,pcm,(int)length,d->ntp_time_local);
    } else if(d->ct==1) {
        if(d->data_len<=0 || d->data_len>16384 || d->data_len%4) { event("Error: invalid PCM block");return; }
        packet(1,d->data,d->data_len,d->ntp_time_local);
    } else if(d->ct==8 || d->ct==4) packet(d->ct,d->data,d->data_len,d->ntp_time_local);
    else event("Error: unsupported audio compression type");
}
void video(void *, raop_ntp_t *, video_decode_struct *d) {
    if (d->is_h265) { event("Error: HEVC is not enabled"); return; }
    packet(0, d->data, d->data_len, d->ntp_time_local);
}
void format(void *, unsigned char *ct, unsigned short *spf, bool *, bool *, uint64_t *af) {
    if(!cinema_audio_format_valid(*ct,*spf,*af)) {
        char message[128];snprintf(message,sizeof(message),"Error: audio format unsupported (ct=%u spf=%u format=%llu)",
            (unsigned)*ct,(unsigned)*spf,(unsigned long long)*af);event(message);
    }
}
void noop(void *) {}
void audioFlush(void *) {
    { std::lock_guard<std::mutex> lock(audioMutex);if(alac) alac->flush(); }
    event("Audio flush");
}
void disconnected(void *) {}
void reset(void *, int) { event("Video interrupted"); }
void videoReset(void *, reset_type_t type) {
    if(type==RESET_TYPE_RTP_SHUTDOWN || type==RESET_TYPE_NOHOLD) event("Video ended");
    // HLS-related resets must not stop an unrelated RAOP audio stream.
}
void admit(void *, char *id, char *, char *name, bool *accepted) {
    std::lock_guard<std::mutex> lock(senderMutex);
    std::string incoming = id ? std::string(id).substr(0,128) : "unknown";
    *accepted = senderId.empty() || senderId == incoming;
    if (*accepted) {
        senderId=incoming;
        std::string label;
        if(name) for(size_t i=0;name[i] && i<48;i++) {
            unsigned char ch=(unsigned char)name[i]; if(ch>=32 && ch<127) label+=(char)ch;
        }
        event((std::string("Connecting · ")+(label.empty()?"sender session requested":label)).c_str());
    }
}
void unsupported(void *, const char *, float) { event("Error: app-specific HLS streaming is not supported"); }
void scrub(void *, float) {}
void rate(void *, float) {}
void info(void *, playback_info_t *p) { memset(p,0,sizeof(*p)); }
float playlist(void *) { return 0; }
double volume(void *) { return 0; }
void setVolume(void *, float db) {
    if(!running) return;
    Env e;if(e.env) e.env->CallVoidMethod(listener,volumeMethod,db);
    if(e.env && e.env->ExceptionCheck()) e.env->ExceptionClear();
}
void pin(void *, char *p) { event((std::string("Pairing PIN: ")+p).c_str()); }
// Matches upstream's default without a registration allowlist. PIN behavior must be device-tested.
bool registered(void *, const char *) { return true; }
void profile(void *, const char **p) { *p = ""; }
int size(void *, video_codec_t codec, unsigned short, unsigned short, unsigned short,
         unsigned short, unsigned short w, unsigned short h) {
    if (codec != VIDEO_CODEC_H264 || !w || !h || w > 1920 || h > 1920) {
        event("Error: unsupported video dimensions or codec"); return -1;
    }
    Env e; if (e.env) e.env->CallVoidMethod(listener, sizeMethod, int(w), int(h));
    if (e.env && e.env->ExceptionCheck()) e.env->ExceptionClear();
    return 0;
}
void cleanup(JNIEnv *env) {
    running = false;
    if (discovery) { dnssd_unregister_airplay(discovery); dnssd_unregister_raop(discovery); }
    if (server) { raop_destroy(server); server = nullptr; }
    if (discovery) { dnssd_destroy(discovery); discovery = nullptr; }
    if (listener) { env->DeleteGlobalRef(listener); listener = nullptr; }
    senderId.clear();
    std::lock_guard<std::mutex> lock(audioMutex);alac.reset();
}
}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *v, void *) { vm=v; return JNI_VERSION_1_6; }
extern "C" JNIEXPORT jstring JNICALL
Java_com_gabriel_cinemaair_Receiver_nativeStart(JNIEnv *env, jobject self, jstring key, jbyteArray id, jint width, jint height) {
    if (server) return env->NewStringUTF("Already started");
    if(!((width==1280 && height==720)||(width==1920 && height==1080))) return env->NewStringUTF("Unsupported quality selection");
    listener = env->NewGlobalRef(self);
    jclass cls = env->GetObjectClass(self);
    packetMethod = env->GetMethodID(cls, "onPacket", "(I[BJ)V");
    eventMethod = env->GetMethodID(cls, "onEvent", "(Ljava/lang/String;)V");
    sizeMethod = env->GetMethodID(cls, "onSize", "(II)V");
    volumeMethod = env->GetMethodID(cls, "onVolume", "(F)V");
    env->DeleteLocalRef(cls);
    if (!packetMethod || !eventMethod || !sizeMethod || !volumeMethod || env->GetArrayLength(id)!=6)
        return env->NewStringUTF("JNI initialization failed");
    char hardware[6]; env->GetByteArrayRegion(id, 0, 6, reinterpret_cast<jbyte *>(hardware));
    int error=0;
    discovery=dnssd_init("Cinema Air", 10, hardware, 6, 1, &error);
    if (!discovery) { cleanup(env); return env->NewStringUTF("mDNS initialization failed"); }
    // Remove documented unsupported desktop features; preserve protocol/authentication bits.
    cinema_capabilities(discovery);
    raop_callbacks_t c{};
    c.audio_process=audio; c.video_process=video; c.video_set_codec_and_size=size;
    c.conn_init=noop; c.conn_destroy=disconnected; c.conn_reset=reset; c.conn_feedback=noop;
    c.video_reset=videoReset; c.video_pause=noop; c.video_resume=noop;
    c.audio_flush=audioFlush; c.video_flush=noop; c.audio_set_client_volume=volume;
    c.audio_set_volume=setVolume; c.display_pin=pin; c.check_register=registered;
    c.get_custom_profile=profile; c.audio_get_format=format;
    c.report_client_request=admit;
    c.on_video_play=unsupported; c.on_video_scrub=scrub; c.on_video_rate=rate;
    c.on_video_stop=noop; c.on_video_playlist_remove=playlist;
    c.on_video_acquire_playback_info=info;
    server=raop_init(&c);
    if (!server) { cleanup(env); return env->NewStringUTF("Protocol initialization failed"); }
    raop_set_log_level(server, 0); // No protocol dumps, keys, sender names or screen contents in logs.
    const char *path=env->GetStringUTFChars(key,nullptr);
    char address[18]; snprintf(address,sizeof(address),"%02x:%02x:%02x:%02x:%02x:%02x",
        (unsigned char)hardware[0],(unsigned char)hardware[1],(unsigned char)hardware[2],
        (unsigned char)hardware[3],(unsigned char)hardware[4],(unsigned char)hardware[5]);
    int result=raop_init2(server,0,address,path);
    env->ReleaseStringUTFChars(key,path);
    if (result) { cleanup(env); return env->NewStringUTF("Could not create private pairing identity"); }
    raop_set_dnssd(server,discovery);
    cinema_display_profile(server,width,height);
    raop_set_plist(server,"pin",0);
    unsigned short port=0;
    raop_ntp_global_init();
    if (raop_start_httpd(server,&port)<0) { cleanup(env); return env->NewStringUTF("Could not bind receiver socket"); }
    raop_set_port(server,port);
    if (dnssd_register_raop(discovery,port) || dnssd_register_airplay(discovery,port)) {
        cleanup(env); return env->NewStringUTF("Could not publish local-network services");
    }
    running=true;
    return nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_com_gabriel_cinemaair_Receiver_nativeStop(JNIEnv *env,jobject) { cleanup(env); }
