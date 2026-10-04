// Test-harness virtual touchscreen for the Android guest (uinput, protocol B multitouch).
// Listens on 127.0.0.1:7070; commands (one per line, raw panel coordinates):
//   d X Y  -> finger down     m X Y -> move     u -> finger up     s MS -> sleep
// Replies "ok\n" to every line so the client can pace itself.
#include <fcntl.h>
#include <linux/uinput.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static int ufd;
static void emit(int type, int code, int val) {
    struct input_event ev; memset(&ev, 0, sizeof ev);
    ev.type = type; ev.code = code; ev.value = val;
    if (write(ufd, &ev, sizeof ev) < 0) perror("write");
}
static void absinfo(int code, int max) {
    struct uinput_abs_setup a; memset(&a, 0, sizeof a);
    a.code = code; a.absinfo.minimum = 0; a.absinfo.maximum = max;
    ioctl(ufd, UI_ABS_SETUP, &a);
}
int main(int argc, char **argv) {
    int W = argc > 1 ? atoi(argv[1]) : 720, H = argc > 2 ? atoi(argv[2]) : 1600;
    ufd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (ufd < 0) { perror("open /dev/uinput"); return 1; }
    ioctl(ufd, UI_SET_EVBIT, EV_KEY); ioctl(ufd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(ufd, UI_SET_EVBIT, EV_ABS);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_SLOT); ioctl(ufd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_POSITION_X); ioctl(ufd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
    ioctl(ufd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
    absinfo(ABS_MT_SLOT, 9); absinfo(ABS_MT_TRACKING_ID, 65535);
    absinfo(ABS_MT_POSITION_X, W - 1); absinfo(ABS_MT_POSITION_Y, H - 1);
    struct uinput_setup us; memset(&us, 0, sizeof us);
    us.id.bustype = BUS_VIRTUAL; us.id.vendor = 0x1d6b; us.id.product = 0x0f0c;
    strcpy(us.name, "fc-test-touchscreen");
    ioctl(ufd, UI_DEV_SETUP, &us);
    if (ioctl(ufd, UI_DEV_CREATE) < 0) { perror("UI_DEV_CREATE"); return 1; }
    int srv = socket(AF_INET, SOCK_STREAM, 0), one = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    struct sockaddr_in sa; memset(&sa, 0, sizeof sa);
    sa.sin_family = AF_INET; sa.sin_port = htons(7070); sa.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(srv, (struct sockaddr *)&sa, sizeof sa) < 0 || listen(srv, 1) < 0) { perror("bind"); return 1; }
    fprintf(stderr, "fctouch ready %dx%d\n", W, H);
    int tid = 1;
    for (;;) {
        int c = accept(srv, NULL, NULL); if (c < 0) continue;
        FILE *in = fdopen(c, "r+"); char line[128];
        while (fgets(line, sizeof line, in)) {
            int x = 0, y = 0;
            if (line[0] == 'd' && sscanf(line + 1, "%d %d", &x, &y) == 2) {
                emit(EV_ABS, ABS_MT_SLOT, 0); emit(EV_ABS, ABS_MT_TRACKING_ID, tid++);
                emit(EV_ABS, ABS_MT_POSITION_X, x); emit(EV_ABS, ABS_MT_POSITION_Y, y);
                emit(EV_KEY, BTN_TOUCH, 1); emit(EV_SYN, SYN_REPORT, 0);
            } else if (line[0] == 'm' && sscanf(line + 1, "%d %d", &x, &y) == 2) {
                emit(EV_ABS, ABS_MT_SLOT, 0);
                emit(EV_ABS, ABS_MT_POSITION_X, x); emit(EV_ABS, ABS_MT_POSITION_Y, y);
                emit(EV_SYN, SYN_REPORT, 0);
            } else if (line[0] == 'u') {
                emit(EV_ABS, ABS_MT_SLOT, 0); emit(EV_ABS, ABS_MT_TRACKING_ID, -1);
                emit(EV_KEY, BTN_TOUCH, 0); emit(EV_SYN, SYN_REPORT, 0);
            } else if (line[0] == 's' && sscanf(line + 1, "%d", &x) == 1) {
                usleep(x * 1000);
            }
            fputs("ok\n", in); fflush(in);
        }
        fclose(in);
    }
}
