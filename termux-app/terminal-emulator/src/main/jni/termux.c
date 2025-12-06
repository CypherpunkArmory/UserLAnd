/*
 * termux.c – PTY + subprocess JNI backend for Termux
 *
 * This file is part of Termux and is licensed under the
 * GNU General Public License, version 3 or later.
 * See the project root LICENSE file for details.
 *
 * Modifications / hardening:
 *   © 2025 Rafael Melo Reis (∆RafaelVerboΩ)
 *   - Better error handling for PTY and fork()
 *   - Safer JNI string handling and bugfix for cwd release
 *   - waitpid() robustness (EINTR)
 *   - Minor cleanups for descriptor and signal handling
 */

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define TERMUX_UNUSED(x) x __attribute__((__unused__))

#ifdef __APPLE__
# define LACKS_PTSNAME_R
#endif

static int throw_runtime_exception(JNIEnv* env, const char* message)
{
    if (!env) return -1;

    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exClass == NULL) {
        // JVM already in error state.
        return -1;
    }

    (*env)->ThrowNew(env, exClass, message ? message : "Native error");
    return -1;
}

static int create_subprocess(JNIEnv* env,
                             const char* cmd,
                             const char* cwd,
                             char* const argv[],
                             char** envp,
                             int* pProcessId,
                             jint rows,
                             jint columns)
{
    if (!cmd || !cwd || !pProcessId) {
        return throw_runtime_exception(env, "create_subprocess: cmd/cwd/processId is null");
    }

    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        return throw_runtime_exception(env, "Cannot open /dev/ptmx");
    }

#ifdef LACKS_PTSNAME_R
    char* devname;
#else
    char devname[64];
#endif

    if (grantpt(ptm) || unlockpt(ptm) ||
#ifdef LACKS_PTSNAME_R
        (devname = ptsname(ptm)) == NULL
#else
        ptsname_r(ptm, devname, sizeof(devname))
#endif
    ) {
        int saved_errno = errno;
        close(ptm);
        errno = saved_errno;
        return throw_runtime_exception(env,
                "Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
    }

    // Enable UTF-8 mode and disable flow control to prevent Ctrl+S from locking up the display.
    struct termios tios;
    if (tcgetattr(ptm, &tios) == 0) {
        tios.c_iflag |= IUTF8;
        tios.c_iflag &= ~(IXON | IXOFF);
        (void) tcsetattr(ptm, TCSANOW, &tios);
    }

    // Set initial winsize.
    struct winsize sz;
    memset(&sz, 0, sizeof(sz));
    sz.ws_row = (unsigned short) rows;
    sz.ws_col = (unsigned short) columns;
    (void) ioctl(ptm, TIOCSWINSZ, &sz);

    pid_t pid = fork();
    if (pid < 0) {
        int saved_errno = errno;
        close(ptm);
        errno = saved_errno;
        return throw_runtime_exception(env, "Fork failed");
    } else if (pid > 0) {
        // Parent.
        *pProcessId = (int) pid;
        return ptm;
    } else {
        // Child.

        // Clear signals which the Android java process may have blocked.
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, NULL);

        // We no longer need the master in the child.
        close(ptm);

        setsid();

#ifdef LACKS_PTSNAME_R
        int pts = open(devname, O_RDWR);
#else
        int pts = open(devname, O_RDWR | O_CLOEXEC);
#endif
        if (pts < 0) _exit(127);

        // Use the pty as stdin/stdout/stderr.
        if (dup2(pts, STDIN_FILENO)  < 0 ||
            dup2(pts, STDOUT_FILENO) < 0 ||
            dup2(pts, STDERR_FILENO) < 0) {
            _exit(127);
        }

        // Close any remaining fds except std{in,out,err} and self_dir.
        DIR* self_dir = opendir("/proc/self/fd");
        if (self_dir != NULL) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent* entry;
            while ((entry = readdir(self_dir)) != NULL) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }

        // At this point pts has been dup2'ed to 0/1/2, safe to close.
        close(pts);

        // Reset environment to known state, then apply envp.
#ifdef __ANDROID__
        clearenv();
#endif
        if (envp) {
            for (char** e = envp; *e; ++e) {
                // putenv() expects "name=value"; envp entries already are.
                putenv(*e);
            }
        }

        if (chdir(cwd) != 0) {
            fprintf(stderr, "chdir(\"%s\"): %s\n", cwd, strerror(errno));
            fflush(stderr);
        }

        execvp(cmd, argv);

        // Only reached if execvp fails.
        fprintf(stderr, "exec(\"%s\"): %s\n", cmd, strerror(errno));
        fflush(stderr);
        _exit(127);
    }
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_createSubprocess(
        JNIEnv* env,
        jclass TERMUX_UNUSED(clazz),
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray envVars,
        jintArray processIdArray,
        jint rows,
        jint columns)
{
    if (!env || !cmd || !cwd || !processIdArray) {
        return throw_runtime_exception(env, "JNI_createSubprocess: null mandatory argument");
    }

    jsize size = args ? (*env)->GetArrayLength(env, args) : 0;
    char** argv = NULL;
    char** envp = NULL;

    // Build argv.
    if (size > 0) {
        argv = (char**) malloc((size + 1) * sizeof(char*));
        if (!argv) {
            return throw_runtime_exception(env, "Couldn't allocate argv array");
        }
        for (jsize i = 0; i < size; ++i) {
            jstring arg_java_string = (jstring) (*env)->GetObjectArrayElement(env, args, i);
            if (!arg_java_string) {
                for (jsize j = 0; j < i; ++j) free(argv[j]);
                free(argv);
                return throw_runtime_exception(env, "Null argument in args array");
            }

            const char* arg_utf8 = (*env)->GetStringUTFChars(env, arg_java_string, NULL);
            if (!arg_utf8) {
                for (jsize j = 0; j < i; ++j) free(argv[j]);
                free(argv);
                return throw_runtime_exception(env, "GetStringUTFChars() failed for argv");
            }
            argv[i] = strdup(arg_utf8);
            (*env)->ReleaseStringUTFChars(env, arg_java_string, arg_utf8);
            if (!argv[i]) {
                for (jsize j = 0; j <= i; ++j) free(argv[j]);
                free(argv);
                return throw_runtime_exception(env, "strdup() failed for argv");
            }
        }
        argv[size] = NULL;
    }

    // Build envp.
    size = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    if (size > 0) {
        envp = (char**) malloc((size + 1) * sizeof(char*));
        if (!envp) {
            if (argv) {
                for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                free(argv);
            }
            return throw_runtime_exception(env, "malloc() for envp array failed");
        }
        for (jsize i = 0; i < size; ++i) {
            jstring env_java_string = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
            if (!env_java_string) {
                for (jsize j = 0; j < i; ++j) free(envp[j]);
                free(envp);
                if (argv) {
                    for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                    free(argv);
                }
                return throw_runtime_exception(env, "Null element in envVars array");
            }

            const char* env_utf8 = (*env)->GetStringUTFChars(env, env_java_string, NULL);
            if (!env_utf8) {
                for (jsize j = 0; j < i; ++j) free(envp[j]);
                free(envp);
                if (argv) {
                    for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                    free(argv);
                }
                return throw_runtime_exception(env, "GetStringUTFChars() failed for env");
            }
            envp[i] = strdup(env_utf8);
            (*env)->ReleaseStringUTFChars(env, env_java_string, env_utf8);
            if (!envp[i]) {
                for (jsize j = 0; j <= i; ++j) free(envp[j]);
                free(envp);
                if (argv) {
                    for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                    free(argv);
                }
                return throw_runtime_exception(env, "strdup() failed for env");
            }
        }
        envp[size] = NULL;
    }

    int procId = 0;
    const char* cmd_cwd = (*env)->GetStringUTFChars(env, cwd, NULL);
    if (!cmd_cwd) {
        if (argv) {
            for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
            free(argv);
        }
        if (envp) {
            for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
            free(envp);
        }
        return throw_runtime_exception(env, "GetStringUTFChars() failed for cwd");
    }

    const char* cmd_utf8 = (*env)->GetStringUTFChars(env, cmd, NULL);
    if (!cmd_utf8) {
        (*env)->ReleaseStringUTFChars(env, cwd, cmd_cwd);
        if (argv) {
            for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
            free(argv);
        }
        if (envp) {
            for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
            free(envp);
        }
        return throw_runtime_exception(env, "GetStringUTFChars() failed for cmd");
    }

    int ptm = create_subprocess(env, cmd_utf8, cmd_cwd, argv, envp, &procId, rows, columns);

    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
    (*env)->ReleaseStringUTFChars(env, cwd, cmd_cwd);

    // Free argv/envp strings and arrays (parent side).
    if (argv) {
        for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
        free(argv);
    }
    if (envp) {
        for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
        free(envp);
    }

    if ((*env)->ExceptionCheck(env)) {
        // create_subprocess already threw; propagate.
        return -1;
    }

    int* pProcId = (int*) (*env)->GetPrimitiveArrayCritical(env, processIdArray, NULL);
    if (!pProcId) {
        return throw_runtime_exception(env,
                "JNI call GetPrimitiveArrayCritical(processIdArray, NULL) failed");
    }

    *pProcId = procId;
    (*env)->ReleasePrimitiveArrayCritical(env, processIdArray, pProcId, 0);

    return ptm;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyWindowSize(
        JNIEnv* TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint fd,
        jint rows,
        jint cols)
{
    struct winsize sz;
    memset(&sz, 0, sizeof(sz));
    sz.ws_row = (unsigned short) rows;
    sz.ws_col = (unsigned short) cols;
    (void) ioctl(fd, TIOCSWINSZ, &sz);
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyUTF8Mode(
        JNIEnv* TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint fd)
{
    struct termios tios;
    if (tcgetattr(fd, &tios) != 0) return;

    if ((tios.c_iflag & IUTF8) == 0) {
        tios.c_iflag |= IUTF8;
        (void) tcsetattr(fd, TCSANOW, &tios);
    }
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_waitFor(
        JNIEnv* TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint pid)
{
    int status = 0;
    pid_t waited;

    do {
        waited = waitpid((pid_t) pid, &status, 0);
    } while (waited == -1 && errno == EINTR);

    if (waited == -1) {
        // Could not wait on pid – return a generic error.
        return -1;
    }

    if (WIFEXITED(status)) {
        return (jint) WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return (jint) -WTERMSIG(status);
    } else {
        // Should never happen.
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_close(
        JNIEnv* TERMUX_UNUSED(env),
        jclass TERMUX_UNUSED(clazz),
        jint fileDescriptor)
{
    if (fileDescriptor >= 0) {
        (void) close(fileDescriptor);
    }
}
