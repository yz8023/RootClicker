package com.roottap.clicker.inject;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;

/**
 * Root side touch injector.
 *
 * <p>This class is <b>not</b> part of the running app process. {@code RootShell} starts it with
 * {@code app_process} (or {@code dalvikvm}) from a root shell, so the process runs as uid 0 and
 * {@code InputManager.injectInputEvent} is allowed to inject events into whatever window is on
 * top. No accessibility service, no UI automation, no {@code input} binary startup cost.</p>
 *
 * <p>Protocol: one command per line on stdin, replies on stdout.</p>
 * <pre>
 *   T x y durationMs            single tap (durationMs &gt;= 600 is a long press)
 *   M durationMs x1 y1 ...     simultaneous multi finger tap
 *   S x1 y1 x2 y2 durationMs    press / drag from the first to the second point
 *   P                           ping, answers "ok"
 *   Q                           quit
 * </pre>
 */
public final class Injector {

    private static final int SOURCE = InputDevice.SOURCE_TOUCHSCREEN;
    /** InputManager.INJECT_INPUT_EVENT_MODE_ASYNC */
    private static final int MODE_ASYNC = 0;
    private static final float PRECISION = 1f;
    private static final float SIZE = 0.02f;
    private static final float PRESSURE = 1f;

    private static Object inputManager;
    private static Method injectMethod;
    private static int deviceId = 0;
    private static final PrintWriter OUT = new PrintWriter(System.out, true);

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            try {
                deviceId = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                // keep the default virtual device id
            }
        }
        try {
            init();
        } catch (Throwable t) {
            OUT.println("ERR " + describe(t));
            return;
        }
        OUT.println("READY");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!handle(line)) {
                    return;
                }
            }
        } catch (Throwable t) {
            OUT.println("ERR " + describe(t));
        }
    }

    /**
     * Art blocks most hidden APIs for app processes. A process started with `app_process` is not
     * an app, but some ROMs still enforce the list, so the exemptions are lifted when possible.
     */
    private static void relaxHiddenApi() {
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);
            Method setExemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setExemptions.setAccessible(true);
            setExemptions.invoke(runtime, (Object) new String[]{"L"});
        } catch (Throwable ignored) {
            // Not fatal: the injector only needs the InputManager reflection below.
        }
    }

    private static void init() throws Exception {
        relaxHiddenApi();
        Class<?> cls = Class.forName("android.hardware.input.InputManager");
        Method getInstance = cls.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        inputManager = getInstance.invoke(null);
        if (inputManager == null) {
            throw new IllegalStateException("InputManager.getInstance() returned null");
        }
        Method found = null;
        for (Method m : cls.getMethods()) {
            if ("injectInputEvent".equals(m.getName()) && m.getParameterTypes().length == 2) {
                found = m;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("injectInputEvent not found");
        }
        found.setAccessible(true);
        injectMethod = found;
    }

    /** @return false when the process has to exit. */
    private static boolean handle(String line) {
        String[] p = line.split("\\s+");
        try {
            switch (p[0]) {
                case "T": {
                    float x = Float.parseFloat(p[1]);
                    float y = Float.parseFloat(p[2]);
                    long duration = p.length > 3 ? Long.parseLong(p[3]) : 40L;
                    tap(x, y, duration);
                    break;
                }
                case "M": {
                    long duration = Long.parseLong(p[1]);
                    int count = (p.length - 2) / 2;
                    if (count <= 0) {
                        break;
                    }
                    float[] xs = new float[count];
                    float[] ys = new float[count];
                    for (int i = 0; i < count; i++) {
                        xs[i] = Float.parseFloat(p[2 + i * 2]);
                        ys[i] = Float.parseFloat(p[3 + i * 2]);
                    }
                    multiTap(xs, ys, duration);
                    break;
                }
                case "S": {
                    float x1 = Float.parseFloat(p[1]);
                    float y1 = Float.parseFloat(p[2]);
                    float x2 = Float.parseFloat(p[3]);
                    float y2 = Float.parseFloat(p[4]);
                    long duration = Long.parseLong(p[5]);
                    stroke(x1, y1, x2, y2, duration);
                    break;
                }
                case "P":
                    break;
                case "Q":
                    return false;
                default:
                    OUT.println("ERR unknown command " + p[0]);
                    return true;
            }
            OUT.println("ok");
        } catch (Throwable t) {
            OUT.println("ERR " + describe(t));
        }
        return true;
    }

    private static void tap(float x, float y, long duration) {
        long downTime = SystemClock.uptimeMillis();
        float[] xs = {x};
        float[] ys = {y};
        send(obtain(downTime, downTime, MotionEvent.ACTION_DOWN, xs, ys, 1, -1));
        if (duration > 0) {
            sleep(duration);
        }
        send(obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, xs, ys, 1, -1));
    }

    private static void multiTap(float[] xs, float[] ys, long duration) {
        int count = xs.length;
        long downTime = SystemClock.uptimeMillis();
        // First finger goes down, the rest follow as POINTER_DOWN so apps see a real
        // multi touch gesture instead of one touch that teleports.
        for (int i = 0; i < count; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN;
            send(obtain(downTime, SystemClock.uptimeMillis(), action,
                    xs, ys, i + 1, i == 0 ? -1 : i));
        }
        if (duration > 0) {
            sleep(duration);
        }
        for (int i = count - 1; i >= 0; i--) {
            int action = i == 0 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP;
            send(obtain(downTime, SystemClock.uptimeMillis(), action,
                    xs, ys, i + 1, i == 0 ? -1 : i));
        }
    }

    private static void stroke(float x1, float y1, float x2, float y2, long duration) {
        long downTime = SystemClock.uptimeMillis();
        float[] xs = {x1};
        float[] ys = {y1};
        send(obtain(downTime, downTime, MotionEvent.ACTION_DOWN, xs, ys, 1, -1));
        if (duration <= 0) {
            send(obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, xs, ys, 1, -1));
            return;
        }
        long start = SystemClock.uptimeMillis();
        long steps = Math.max(1L, Math.min(240L, duration / 8L));
        for (long i = 1; i <= steps; i++) {
            float f = (float) i / (float) steps;
            float x = x1 + (x2 - x1) * f;
            float y = y1 + (y2 - y1) * f;
            long target = start + (duration * i) / steps;
            long left = target - SystemClock.uptimeMillis();
            if (left > 0) {
                sleep(left);
            }
            float[] mx = {x};
            float[] my = {y};
            send(obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, mx, my, 1, -1));
        }
        float[] ex = {x2};
        float[] ey = {y2};
        send(obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, ex, ey, 1, -1));
    }

    private static MotionEvent obtain(long downTime, long eventTime, int action,
                                      float[] xs, float[] ys, int count, int index) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = i;
            pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
            properties[i] = pp;
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = xs[i];
            pc.y = ys[i];
            pc.pressure = PRESSURE;
            pc.size = SIZE;
            coords[i] = pc;
        }
        int resolved = action;
        if (index >= 0
                && (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)) {
            resolved = action | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }
        return MotionEvent.obtain(downTime, eventTime, resolved, count, properties, coords,
                0, 0, PRECISION, PRECISION, deviceId, 0, SOURCE, 0);
    }

    private static void send(MotionEvent event) {
        try {
            injectMethod.invoke(inputManager, event, MODE_ASYNC);
        } catch (Throwable t) {
            OUT.println("ERR inject " + describe(t));
        } finally {
            event.recycle();
        }
    }

    private static void sleep(long ms) {
        long end = SystemClock.uptimeMillis() + ms;
        while (true) {
            long left = end - SystemClock.uptimeMillis();
            if (left <= 0) {
                return;
            }
            try {
                Thread.sleep(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : (": " + message));
    }
}
