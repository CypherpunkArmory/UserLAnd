#include <stdio.h>
#include <stdint.h>
#include <math.h>

int32_t M[42][8];
float X[42], Y[42];

#define CX 500.0f
#define CY 500.0f
#define RAD 400.0f
#define SQ_PI 1.77245385f

const char* CLR[] = {"#4EC8E3", "#FFD700", "#FF7F50", "#ADFF2F"};

int main() {
    if(fread(M, sizeof(int32_t), 42 * 8, stdin) != 42*8) return 1;

    for(int i=0; i<42; ++i) {
        float ang_deg = (float)(M[i][0]) * (360.0f/42.0f) * SQ_PI;
        float ang_rad = ang_deg * (3.14159265f / 180.0f);
        X[i] = CX + cosf(ang_rad) * RAD;
        Y[i] = CY + sinf(ang_rad) * RAD;
    }

    printf("<svg width='1000' height='1000' xmlns='http://www.w3.org/2000/svg' style='background:#1a1a1a'>\n");
    printf("<g stroke='#555' stroke-width='1' opacity='0.4'>\n");
    for(int i=0; i<42; ++i) {
        for(int j=i+1; j<42; ++j) {
            if(M[i][2] == M[j][2]) { 
                printf("<line x1='%.1f' y1='%.1f' x2='%.1f' y2='%.1f'/>\n", X[i], Y[i], X[j], Y[j]);
            }
        }
    }
    printf("</g>\n");
    for(int i=0; i<42; ++i) {
        int geo_idx = (M[i][2] - 1) % 4;
        printf("<circle cx='%.1f' cy='%.1f' r='8' fill='%s'/>\n", X[i], Y[i], CLR[geo_idx]);
        printf("<text x='%.1f' y='%.1f' fill='#fff' font-size='10' font-family='monospace' text-anchor='middle' dy='-12'>%d</text>\n", 
               X[i], Y[i], M[i][0]);
    }
    printf("</svg>\n");
    return 0;
}
