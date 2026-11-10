// The manager's Slint platform on Sailfish OS (meson -Dwlshell=true): Lipstick (QtWayland 5.6) offers wl_shell only, no xdg_wm_base, which
// Slint's winit backend needs, and Sailfish's Qt 5.6 is older than Slint's Qt backend takes (5.15). So the window is a wl_shell toplevel at
// the output's size (Lipstick shows every application full screen and sends no configure, as bridge/shell/wlshell.c does), drawn by Slint's
// software renderer into wl_shm buffers; touches are the pointer (Lipstick is touch only). Slint's tasks and timers run in this poll loop.
#include "wlshell.h"

#include <slint-platform.h>
#include <wayland-client.h>

#include <poll.h>
#include <sys/eventfd.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

namespace {

using slint::platform::SoftwareRenderer;

struct Display {
    wl_display* display = nullptr;
    wl_compositor* compositor = nullptr;
    wl_shell* shell = nullptr;
    wl_shm* shm = nullptr;
    wl_seat* seat = nullptr;
    wl_touch* touch = nullptr;
    wl_output* output = nullptr;
    int width = 0, height = 0;  // the output's current mode
};
Display d;

void outputGeometry(void*, wl_output*, int32_t, int32_t, int32_t, int32_t, int32_t, const char*, const char*, int32_t) {}
void outputMode(void*, wl_output*, uint32_t flags, int32_t w, int32_t h, int32_t) {
    if (flags & WL_OUTPUT_MODE_CURRENT) d.width = w, d.height = h;
}
void outputDone(void*, wl_output*) {}
void outputScale(void*, wl_output*, int32_t) {}
const wl_output_listener outputListener = {outputGeometry, outputMode, outputDone, outputScale};

void global(void*, wl_registry* r, uint32_t name, const char* iface, uint32_t version) {
    std::string i = iface;
    if (i == "wl_compositor") d.compositor = (wl_compositor*)wl_registry_bind(r, name, &wl_compositor_interface, 1);
    else if (i == "wl_shell") d.shell = (wl_shell*)wl_registry_bind(r, name, &wl_shell_interface, 1);
    else if (i == "wl_shm") d.shm = (wl_shm*)wl_registry_bind(r, name, &wl_shm_interface, 1);
    else if (i == "wl_seat" && !d.seat) d.seat = (wl_seat*)wl_registry_bind(r, name, &wl_seat_interface, std::min(version, 3u));
    else if (i == "wl_output" && !d.output) {
        d.output = (wl_output*)wl_registry_bind(r, name, &wl_output_interface, std::min(version, 2u));
        wl_output_add_listener(d.output, &outputListener, nullptr);
    }
}
void globalRemove(void*, wl_registry*, uint32_t) {}
const wl_registry_listener registryListener = {global, globalRemove};

// Silica's pixel ratio is 1 on the Jolla 1, an hdpi (1.5) phone: Slint's logical pixel is Android's dp, so the scale is that ratio times 1.5
float sailfishScale() {
    if (const char* s = getenv("SLINT_SCALE_FACTOR"); s && atof(s) > 0) return atof(s);
    float ratio = 0;
    if (FILE* p = popen("dconf read /desktop/sailfish/silica/theme_pixel_ratio 2>/dev/null", "r")) {
        if (fscanf(p, "%f", &ratio) != 1) ratio = 0;
        pclose(p);
    }
    return ratio > 0 ? ratio * 1.5f : std::max(1.0f, d.width / 400.0f);  // no Silica: a phone's ~400 dp width
}

struct Buffer {
    wl_buffer* buffer = nullptr;
    uint32_t* pixels = nullptr;
    bool busy = false;
};

class Window : public slint::platform::WindowAdapter {
public:
    SoftwareRenderer renderer_{SoftwareRenderer::RepaintBufferType::ReusedBuffer};  // into back_, which keeps the last frame
    std::vector<slint::Rgb8Pixel> back_;
    Buffer buffers_[2];
    wl_surface* surface_ = nullptr;
    wl_shell_surface* shellSurface_ = nullptr;
    wl_callback* frame_ = nullptr;
    bool dirty_ = true;
    float scale_ = 1;
    int touchId_ = -1;
    slint::LogicalPosition touchAt_{};

    slint::platform::AbstractRenderer& renderer() override { return renderer_; }
    slint::PhysicalSize size() override { return slint::PhysicalSize({uint32_t(d.width), uint32_t(d.height)}); }
    void request_redraw() override { dirty_ = true; }
    void set_visible(bool visible) override {
        if (!visible || surface_) return;
        scale_ = sailfishScale();
        window().dispatch_scale_factor_change_event(scale_);
        window().dispatch_resize_event(slint::LogicalSize({d.width / scale_, d.height / scale_}));
        surface_ = wl_compositor_create_surface(d.compositor);
        shellSurface_ = wl_shell_get_shell_surface(d.shell, surface_);
        static const wl_shell_surface_listener listener = {
            [](void*, wl_shell_surface* s, uint32_t serial) { wl_shell_surface_pong(s, serial); },
            [](void*, wl_shell_surface*, uint32_t, int32_t, int32_t) {}, [](void*, wl_shell_surface*) {}};
        wl_shell_surface_add_listener(shellSurface_, &listener, this);
        wl_shell_surface_set_title(shellSurface_, "Apklayer");
        wl_shell_surface_set_class(shellSurface_, "apklayer-manager");
        wl_shell_surface_set_toplevel(shellSurface_);
        back_.resize(size_t(d.width) * d.height);
        int stride = d.width * 4, bytes = stride * d.height;
        int fd = memfd_create("apklayer-manager", MFD_CLOEXEC);
        if (fd < 0 || ftruncate(fd, bytes * 2) < 0) { perror("apklayer-manager: shm"); exit(1); }
        auto* map = (uint8_t*)mmap(nullptr, bytes * 2, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        wl_shm_pool* pool = wl_shm_create_pool(d.shm, fd, bytes * 2);
        static const wl_buffer_listener release = {[](void* b, wl_buffer*) { ((Buffer*)b)->busy = false; }};
        for (int i = 0; i < 2; i++) {
            buffers_[i].buffer = wl_shm_pool_create_buffer(pool, i * bytes, d.width, d.height, stride, WL_SHM_FORMAT_XRGB8888);
            buffers_[i].pixels = (uint32_t*)(map + i * bytes);
            wl_buffer_add_listener(buffers_[i].buffer, &release, &buffers_[i]);
        }
        wl_shm_pool_destroy(pool);
        close(fd);
    }

    // a frame when one is due, the compositor took the last one and a buffer is free
    void draw() {
        if (!surface_ || !dirty_ || frame_) return;
        Buffer* b = !buffers_[0].busy ? &buffers_[0] : !buffers_[1].busy ? &buffers_[1] : nullptr;
        if (!b) return;
        dirty_ = false;
        renderer_.render(back_, d.width);
        for (size_t i = 0; i < back_.size(); i++) b->pixels[i] = 0xff000000u | back_[i].r << 16 | back_[i].g << 8 | back_[i].b;
        b->busy = true;
        wl_surface_attach(surface_, b->buffer, 0, 0);
        wl_surface_damage(surface_, 0, 0, d.width, d.height);
        static const wl_callback_listener done = {[](void* w, wl_callback* c, uint32_t) { wl_callback_destroy(c); ((Window*)w)->frame_ = nullptr; }};
        frame_ = wl_surface_frame(surface_);
        wl_callback_add_listener(frame_, &done, this);
        wl_surface_commit(surface_);
        if (window().has_active_animations()) dirty_ = true;
    }

    slint::LogicalPosition at(wl_fixed_t x, wl_fixed_t y) const {
        return slint::LogicalPosition({float(wl_fixed_to_double(x) / scale_), float(wl_fixed_to_double(y) / scale_)});
    }
};
Window* win = nullptr;

// one finger is the pointer; others are ignored
void touchDown(void*, wl_touch*, uint32_t, uint32_t, wl_surface*, int32_t id, wl_fixed_t x, wl_fixed_t y) {
    if (!win || win->touchId_ >= 0) return;
    win->touchId_ = id;
    win->touchAt_ = win->at(x, y);
    win->window().dispatch_pointer_move_event(win->touchAt_);
    win->window().dispatch_pointer_press_event(win->touchAt_, slint::PointerEventButton::Left);
}
void touchUp(void*, wl_touch*, uint32_t, uint32_t, int32_t id) {
    if (!win || id != win->touchId_) return;
    win->touchId_ = -1;
    win->window().dispatch_pointer_release_event(win->touchAt_, slint::PointerEventButton::Left);
    win->window().dispatch_pointer_exit_event();
}
void touchMotion(void*, wl_touch*, uint32_t, int32_t id, wl_fixed_t x, wl_fixed_t y) {
    if (!win || id != win->touchId_) return;
    win->touchAt_ = win->at(x, y);
    win->window().dispatch_pointer_move_event(win->touchAt_);
}
void touchFrame(void*, wl_touch*) {}
void touchCancel(void*, wl_touch*) {
    if (!win || win->touchId_ < 0) return;
    win->touchId_ = -1;
    win->window().dispatch_pointer_exit_event();
}
const wl_touch_listener touchListener = {touchDown, touchUp, touchMotion, touchFrame, touchCancel};

void seatCapabilities(void*, wl_seat* seat, uint32_t caps) {
    if ((caps & WL_SEAT_CAPABILITY_TOUCH) && !d.touch) {
        d.touch = wl_seat_get_touch(seat);
        wl_touch_add_listener(d.touch, &touchListener, nullptr);
    }
}
void seatName(void*, wl_seat*, const char*) {}
const wl_seat_listener seatListener = {seatCapabilities, seatName};

class Platform : public slint::platform::Platform {
    int wake_ = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    std::mutex lock_;
    std::deque<Task> tasks_;
    bool quit_ = false;

public:
    std::unique_ptr<slint::platform::WindowAdapter> create_window_adapter() override {
        auto w = std::make_unique<Window>();
        win = w.get();  // shortcut: one window (the manager has one; dialogs are drawn inside it)
        return w;
    }
    void quit_event_loop() override {
        { std::lock_guard g(lock_); quit_ = true; }
        uint64_t one = 1;
        (void)!write(wake_, &one, sizeof one);
    }
    void run_in_event_loop(Task task) override {
        { std::lock_guard g(lock_); tasks_.push_back(std::move(task)); }
        uint64_t one = 1;
        (void)!write(wake_, &one, sizeof one);
    }
    void run_event_loop() override {
        for (;;) {
            slint::platform::update_timers_and_animations();
            std::deque<Task> due;
            {
                std::lock_guard g(lock_);
                if (quit_) { quit_ = false; return; }
                due.swap(tasks_);
            }
            for (Task& t : due) std::move(t).run();
            if (!due.empty()) continue;  // a task may have quit or queued more
            if (win) win->draw();
            while (wl_display_prepare_read(d.display) != 0) wl_display_dispatch_pending(d.display);
            wl_display_flush(d.display);
            int timeout = -1;
            if (win && win->dirty_ && !win->frame_) timeout = 16;  // a frame due once a buffer is released
            if (auto next = slint::platform::duration_until_next_timer_update())
                timeout = timeout < 0 ? int(std::min<long long>(next->count(), INT32_MAX)) : std::min<int>(timeout, next->count());
            pollfd fds[2] = {{wl_display_get_fd(d.display), POLLIN, 0}, {wake_, POLLIN, 0}};
            poll(fds, 2, timeout);
            if (fds[0].revents & POLLIN) wl_display_read_events(d.display);
            else wl_display_cancel_read(d.display);
            if ((fds[0].revents & (POLLERR | POLLHUP)) || wl_display_dispatch_pending(d.display) < 0) { fprintf(stderr, "apklayer-manager: the compositor closed the window\n"); exit(0); }
            if (fds[1].revents & POLLIN) { uint64_t n; (void)!read(wake_, &n, sizeof n); }
        }
    }
};

}  // namespace

bool manager::useWlShell() {
    d.display = wl_display_connect(nullptr);
    if (!d.display) return false;
    wl_registry* r = wl_display_get_registry(d.display);
    wl_registry_add_listener(r, &registryListener, nullptr);
    wl_display_roundtrip(d.display);
    if (d.seat) wl_seat_add_listener(d.seat, &seatListener, nullptr);
    wl_display_roundtrip(d.display);  // the output's mode, the seat's capabilities
    if (!d.compositor || !d.shell || !d.shm || d.width <= 0) {
        fprintf(stderr, "apklayer-manager: the compositor offers no wl_shell window (wl_compositor, wl_shell, wl_shm, an output)\n");
        return false;
    }
    slint::platform::set_platform(std::make_unique<Platform>());
    return true;
}
