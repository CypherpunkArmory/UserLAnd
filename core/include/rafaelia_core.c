#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>

// MACROS & CONSTANTS
#define N 128       // Nodes (Power of 2 for alignment)
#define W 1024.0f   // Width
#define H 1024.0f   // Height
#define T 4         // Threads
#define ITER 200    // Physics Steps

// GLOBAL STATE (Linear Memory Layout)
// P[i]: x,y | V[i]: vx,vy
float P[N*2], V[N*2];
// M: Adjacency Bitmask (Simplified for dense graph)
uint8_t M[N*N];
// B: Barrier for Sync
pthread_barrier_t B;

// FAST RANDOM (Linear Congruential Generator - No syscalls)
uint32_t _r(uint32_t* s) {
    *s = *s * 1103515245 + 12345;
    return (*s / 65536) % 32768;
}

// KERNEL PHYSICS (Thread Entry)
void* _k(void* a) {
    long id = (long)a;
    int s = (N/T)*id, e = s + (N/T);
    float dx, dy, d2, d, f;
    
    for(int k=0; k<ITER; k++) {
        // 1. REPULSION (Coulomb)
        for(int i=s; i<e; i++) {
            float fx=0, fy=0;
            for(int j=0; j<N; j++) {
                if(i==j) continue;
                dx = P[i*2] - P[j*2];
                dy = P[i*2+1] - P[j*2+1];
                d2 = dx*dx + dy*dy + 0.01f;
                f = 5000.0f / d2; // Force Cte
                fx += (dx * f); fy += (dy * f);
            }
            V[i*2] += fx * 0.01f; V[i*2+1] += fy * 0.01f;
        }
        pthread_barrier_wait(&B);

        // 2. ATTRACTION (Hooke)
        for(int i=s; i<e; i++) {
            for(int j=0; j<N; j++) {
                if(M[i*N+j]) {
                    dx = P[j*2] - P[i*2];
                    dy = P[j*2+1] - P[i*2+1];
                    d = sqrtf(dx*dx + dy*dy + 0.001f);
                    f = (d - 50.0f) * 0.05f; // K_spring
                    V[i*2] += (dx/d)*f*0.1f; V[i*2+1] += (dy/d)*f*0.1f;
                }
            }
        }
        pthread_barrier_wait(&B);

        // 3. INTEGRATION (Verlet-ish)
        for(int i=s; i<e; i++) {
            V[i*2] *= 0.85f; V[i*2+1] *= 0.85f; // Damping
            P[i*2] += V[i*2] * 0.1f;
            P[i*2+1] += V[i*2+1] * 0.1f;
            // Bounds
            if(P[i*2]<0 || P[i*2]>W) V[i*2]*=-1;
            if(P[i*2+1]<0 || P[i*2+1]>H) V[i*2+1]*=-1;
        }
        pthread_barrier_wait(&B);
    }
    return NULL;
}

int main() {
    uint32_t s = 963; // Seed
    // INIT DATA
    for(int i=0; i<N; i++) {
        P[i*2] = (_r(&s)%((int)W)); 
        P[i*2+1] = (_r(&s)%((int)H));
        // Procedural Edges (Ring + Random shortcuts)
        int t = (i+1)%N; M[i*N+t]=1; M[t*N+i]=1;
        if(_r(&s)%10 < 2) { 
            int r = _r(&s)%N; M[i*N+r]=1; M[r*N+i]=1; 
        }
    }

    // RUN THREADS
    pthread_t th[T];
    pthread_barrier_init(&B, NULL, T);
    for(long i=0; i<T; i++) pthread_create(&th[i], NULL, _k, (void*)i);
    for(int i=0; i<T; i++) pthread_join(th[i], NULL);
    pthread_barrier_destroy(&B);

    // OUTPUT SVG (Raw Stream)
    printf("<svg width='%.0f' height='%.0f' style='background:#111'>\n", W, H);
    printf("<g stroke='#444' stroke-width='1'>\n");
    for(int i=0; i<N; i++) 
        for(int j=i+1; j<N; j++) 
            if(M[i*N+j]) printf("<line x1='%.1f' y1='%.1f' x2='%.1f' y2='%.1f'/>\n", P[i*2],P[i*2+1],P[j*2],P[j*2+1]);
    printf("</g><g fill='#00ffcc'>\n");
    for(int i=0; i<N; i++) printf("<circle cx='%.1f' cy='%.1f' r='3'/>\n", P[i*2], P[i*2+1]);
    printf("</g></svg>\n");
    return 0;
}
