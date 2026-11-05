#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <sys/time.h>
#include "raop.h"
#include "capabilities.h"
#include <plist/plist.h>
#define CHECK(x) do { if(!(x)) { fprintf(stderr,"Failed at %d: %s\n",__LINE__,#x);exit(1); } } while(0)
static void audio(void *c,raop_ntp_t *n,audio_decode_struct *d) {}
static void video(void *c,raop_ntp_t *n,video_decode_struct *d) {}
static double volume(void *c) { return 0; }
static void nothing(void *c) {}
int main(void) {
    char tmp[]="/tmp/cinemaair-protocol-XXXXXX";CHECK(mkdtemp(tmp));
    char key[256];snprintf(key,sizeof(key),"%s/pairing.key",tmp);
    char hw[6]={2,0,0,0,0,1};
    for(int cycle=0;cycle<3;cycle++) {
        raop_callbacks_t c={0};c.audio_process=audio;c.video_process=video;
        c.audio_set_client_volume=volume;c.conn_feedback=nothing;
        raop_t *s=raop_init(&c);CHECK(s);raop_set_log_level(s,0);
        CHECK(raop_init2(s,0,"02:00:00:00:00:01",key)==0);
        int err=0;dnssd_t *d=dnssd_init("Cinema Air Test",15,hw,6,1,&err);CHECK(d);
        cinema_capabilities(d);
        raop_set_dnssd(s,d);raop_set_plist(s,"width",1280);raop_set_plist(s,"height",720);
        unsigned short port=0;CHECK(raop_start_httpd(s,&port)>0);CHECK(port>0);raop_set_port(s,port);
        int fd=socket(AF_INET,SOCK_STREAM,0);CHECK(fd>=0);
        struct timeval timeout={2,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
        struct sockaddr_in address={0};address.sin_family=AF_INET;address.sin_port=htons(port);address.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
        CHECK(connect(fd,(struct sockaddr *)&address,sizeof(address))==0);
        const char *request="OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n";
        CHECK(send(fd,request,strlen(request),0)==strlen(request));
        char response[8192]={0};int n=recv(fd,response,sizeof(response)-1,0);CHECK(n>0);
        CHECK(strstr(response,"200 OK"));CHECK(strstr(response,"Public:"));
        request="GET /info RTSP/1.0\r\nCSeq: 2\r\n\r\n";
        CHECK(send(fd,request,strlen(request),0)==strlen(request));
        memset(response,0,sizeof(response));size_t used=0,total=sizeof(response);char *body=NULL;
        do {
            n=recv(fd,response+used,sizeof(response)-1-used,0);CHECK(n>0);used+=n;response[used]=0;
            if(!body) {
                body=strstr(response,"\r\n\r\n");
                if(body) {
                    body+=4;char *length=strstr(response,"Content-Length:");CHECK(length);
                    total=(size_t)(body-response)+strtoul(length+15,NULL,10);CHECK(total<sizeof(response));
                }
            }
        } while(used<total);
        CHECK(strstr(response,"200 OK"));CHECK(body && !memcmp(body,"bplist00",8));
        plist_t metadata=NULL;plist_from_bin(body,(uint32_t)(used-(body-response)),&metadata);CHECK(metadata);
        uint64_t flags=0;plist_get_uint_val(plist_dict_get_item(metadata,"features"),&flags);
        CHECK((flags & (UINT64_C(1)<<20))!=0);CHECK(!(flags & ((UINT64_C(1)<<18)|(UINT64_C(1)<<19))));
        plist_t formats=plist_dict_get_item(metadata,"audioFormats");CHECK(plist_array_get_size(formats)==2);
        for(int i=0;i<2;i++) {
            plist_t f=plist_array_get_item(formats,i);uint64_t mask=0;
            plist_get_uint_val(plist_dict_get_item(f,"audioOutputFormats"),&mask);CHECK(mask==CINEMA_AUDIO_FORMATS);
        }
        plist_free(metadata);
        close(fd);raop_destroy(s);dnssd_destroy(d);
    }
    CHECK(unlink(key)==0);CHECK(rmdir(tmp)==0);
    puts("PASS: three actual RTSP listen / OPTIONS / binary-plist info with AAC-only capabilities / disconnect / stop cycles, persisted pairing identity");
    return 0;
}
