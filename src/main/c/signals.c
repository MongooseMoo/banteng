#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stddef.h>
#include <stdatomic.h>
#include <unistd.h>

/* Process lifetime storage: a signal already in flight may still enter here
 * after restoration. Do not unload this library or recycle the pipe descriptors. */
static int signal_pipe[2] = {-1, -1};
static const int signals[] = {
    SIGHUP, SIGINT, SIGQUIT, SIGILL, SIGBUS, SIGFPE, SIGSEGV,
    SIGUSR1, SIGUSR2, SIGALRM, SIGTERM
};
#define SIGNAL_COUNT (sizeof(signals) / sizeof(signals[0]))
static struct sigaction previous[SIGNAL_COUNT];
static size_t installed;
static atomic_int stopping;

static void receive_signal(int number, siginfo_t *info, void *context) {
    int saved_errno = errno;
    size_t index = 0;
    while (index < SIGNAL_COUNT && signals[index] != number) ++index;
    if (index == SIGNAL_COUNT) return;

    /* kill()/sigqueue() target the server. Hardware exceptions and the JVM's
     * thread-directed suspend/resume signals retain their original handlers. */
    if (info != NULL && (info->si_code == SI_USER || info->si_code == SI_QUEUE)) {
        if (number != SIGFPE) {
            unsigned char value = (unsigned char)number;
            ssize_t ignored = write(signal_pipe[1], &value, sizeof(value));
            (void)ignored; /* Standard signals may coalesce, as may a full pipe. */
        }
    } else if (previous[index].sa_handler != SIG_IGN) {
        if (previous[index].sa_handler == SIG_DFL) {
            sigaction(number, &previous[index], NULL);
            raise(number);
        } else if (previous[index].sa_flags & SA_SIGINFO) {
            previous[index].sa_sigaction(number, info, context);
        } else {
            previous[index].sa_handler(number);
        }
    }
    errno = saved_errno;
}

int banteng_signals_install(void) {
    if (signal_pipe[0] != -1) return -EALREADY;
    if (pipe(signal_pipe) != 0) return -errno;
    if (fcntl(signal_pipe[0], F_SETFD, FD_CLOEXEC) == -1 ||
        fcntl(signal_pipe[1], F_SETFD, FD_CLOEXEC) == -1 ||
        fcntl(signal_pipe[1], F_SETFL, O_NONBLOCK) == -1) return -errno;
    for (size_t index = 0; index < SIGNAL_COUNT; ++index) {
        if (sigaction(signals[index], NULL, &previous[index]) != 0) goto failed;
        struct sigaction action = previous[index];
        if (signals[index] == SIGHUP && action.sa_handler == SIG_IGN) {
            installed = index + 1;
            continue;
        }
        action.sa_sigaction = receive_signal;
        action.sa_flags |= SA_SIGINFO | SA_RESTART;
        if (sigaction(signals[index], &action, NULL) != 0) goto failed;
        installed = index + 1;
    }
    return 0;
failed: {
        int failure = errno;
        while (installed > 0) {
            --installed;
            sigaction(signals[installed], &previous[installed], NULL);
        }
        return -failure;
    }
}

int banteng_signals_next(void) {
    if (atomic_load(&stopping)) return 0;
    unsigned char value;
    ssize_t count;
    do {
        count = read(signal_pipe[0], &value, sizeof(value));
    } while (count < 0 && errno == EINTR);
    return count == 1 ? value : (count == 0 ? 0 : -errno);
}

int banteng_signals_stop(void) {
    atomic_store(&stopping, 1);
    int failure = 0;
    while (installed > 0) {
        --installed;
        if (sigaction(signals[installed], &previous[installed], NULL) != 0) failure = errno;
    }
    unsigned char stop = 0;
    ssize_t ignored = write(signal_pipe[1], &stop, sizeof(stop));
    (void)ignored;
    return -failure;
}
