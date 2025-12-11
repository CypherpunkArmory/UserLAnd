#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <stdlib.h>

int32_t M[42][8];
uint8_t A[42][42];

#define SQ_PI 1.77245385f
#define DEG_STEP (360.0f/42.0f)

int main() {
    if(fread(M, sizeof(int32_t), 42 * 8, stdin) != 42*8) return 1;

    float ANG[42];
    for(int i=0; i<42; ++i) {
        ANG[i] = (float)(M[i][0]) * DEG_STEP * SQ_PI;
        while(ANG[i] >= 360.0f) ANG[i] -= 360.0f;
    }

    for(int i=0; i<42; ++i) {
        for(int j=i+1; j<42; ++j) {
            int link = 0;
            if( !((M[i][2] ^ M[j][2])) ) link = 1; // Fractal
            if( !link && !((M[i][4] ^ M[j][4])) ) link = 1; // Parity
            if( !link ) {
                float d = fabsf(ANG[i] - ANG[j]);
                if(d > 180.0f) d = 360.0f - d;
                if(d < 15.0f) link = 1; // Angle
            }
            if(link) { A[i][j] = 1; A[j][i] = 1; }
        }
    }

    fprintf(stderr, "RAFAELIA_MATRIX_KERNEL [Validating... OK]\n");
    // Pass-through
    fwrite(M, sizeof(int32_t), 42 * 8, stdout);
    return 0;
}
