#include <stdio.h>
#include <stdint.h>

// MATRIX DEFINITIONS (Flattened 42x8)
int32_t M[42][8]; 

void _gen() {
    int c = 0;
    // Combinatorial Logic
    for(int m=0; m<3; ++m)
     for(int f=1; f<=4; ++f)
      for(int r=0; r<10; ++r)
       for(int p=1; p<=2; ++p)
        for(int d=0; d<4; ++d)
         for(int pl=0; pl<2; ++pl) {
             if(c >= 42) return;
             
             if ((m + f + r + p + d) % 7 == 0) {
                 int* row = M[c];
                 row[0] = c + 1; // ID
                 row[1] = m;     // Mode
                 row[2] = f;     // Fractal
                 row[3] = r;     // RafBit
                 row[4] = p;     // Parity
                 row[5] = d;     // Direction
                 row[6] = pl;    // Polarity
                 row[7] = 0;     // Padding
                 c++;
             }
         }
}

int main() {
    _gen();
    // Dump binary directly to stdout
    fwrite(M, sizeof(int32_t), 42 * 8, stdout);
    return 0;
}
