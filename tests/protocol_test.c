#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include "http_request.h"
#include "../native/packet_limits.h"
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"Failed at %d: %s\n",__LINE__,#x); exit(1); } } while(0)
int main(void) {
    http_request_t *r=http_request_init();
    const char *a="OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n";
    for(size_t i=0;i<strlen(a);i++) CHECK(http_request_add_data(r,a+i,1)==0);
    CHECK(http_request_is_complete(r));CHECK(strcmp(http_request_get_method(r),"OPTIONS")==0);
    http_request_destroy(r);
    r=http_request_init();
    a="SETUP / RTSP/1.0\r\nContent-Length: 99\r\n\r\nx";
    CHECK(http_request_add_data(r,a,strlen(a))==0);CHECK(!http_request_is_complete(r));
    http_request_destroy(r);
    r=http_request_init();
    a="SETUP / RTSP/1.0\r\nContent-Length: -1\r\n\r\n";
    CHECK(http_request_add_data(r,a,strlen(a))!=0);CHECK(http_request_has_error(r));
    http_request_destroy(r);
    r=http_request_init();
    CHECK(http_request_add_data(r,"x",SIZE_MAX)!=0);CHECK(http_request_has_error(r));
    http_request_destroy(r);
    r=http_request_init();
    a="POST /pair-setup RTSP/1.0\r\nContent-Length: 2097152\r\n\r\n";
    CHECK(http_request_add_data(r,a,strlen(a))==0);
    char chunk[4096];memset(chunk,0,sizeof(chunk));int ret=0;
    for(int i=0;i<257 && ret==0;i++) ret=http_request_add_data(r,chunk,sizeof(chunk));
    CHECK(ret!=0);CHECK(http_request_has_error(r));http_request_destroy(r);
    uint8_t avc[13]={1,0,0,0,0,0,0,1,0x67,1,0,1,0x68};
    CHECK(cinema_avc_config_valid(avc,sizeof(avc)));
    for(size_t n=0;n<13;n++) CHECK(!cinema_avc_config_valid(avc,n));
    avc[6]=0xff; CHECK(!cinema_avc_config_valid(avc,sizeof(avc)));
    avc[6]=0;avc[11]=0xff;CHECK(!cinema_avc_config_valid(avc,sizeof(avc)));
    puts("PASS: AVC config length boundaries, oversized SPS/PPS");
    puts("PASS: fragmented RTSP, truncated body, malformed length, size_t overflow, cumulative request budget");
    return 0;
}
