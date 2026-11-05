#include "alac_decoder.h"
#include <vector>
#include <fstream>
#include <iterator>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#define CHECK(x) do { if(!(x)) { fprintf(stderr,"Failed at %d: %s\n",__LINE__,#x);exit(1); } } while(0)
static std::vector<uint8_t> read(const char *p) {
    std::ifstream f(p,std::ios::binary);CHECK(f.good());
    return std::vector<uint8_t>(std::istreambuf_iterator<char>(f),{});
}
int main(int argc,char **argv) {
    CHECK(argc==3);auto packet=read(argv[1]),expected=read(argv[2]);
    AlacDecoder decoder;uint8_t pcm[352*4];size_t written=0;
    CHECK(decoder.decode(packet.data(),packet.size(),pcm,sizeof(pcm),written));
    CHECK(written==expected.size());CHECK(!memcmp(pcm,expected.data(),written));
    decoder.flush();CHECK(decoder.decode(packet.data(),packet.size(),pcm,sizeof(pcm),written));
    CHECK(!memcmp(pcm,expected.data(),written));
    CHECK(!decoder.decode(packet.data(),packet.size(),pcm,1,written));
    CHECK(!decoder.decode(packet.data(),65537,pcm,sizeof(pcm),written));
    for(size_t size=0;size<8;size++) CHECK(!decoder.decode(packet.data(),size,pcm,sizeof(pcm),written));
    uint8_t junk[32];
    for(int value=0;value<256;value+=17) {
        memset(junk,value,sizeof(junk));decoder.flush();
        decoder.decode(junk,sizeof(junk),pcm,sizeof(pcm),written);CHECK(written<=sizeof(pcm));
    }
    puts("PASS: real ALAC packet to exact stereo PCM, flush/reuse, truncated/malformed input and output budgets");
}
