package com.backsound.youtube;

/** Time-based gain envelope, independent of server tick rate. */
final class FadeEnvelope {
    private double from = 1, target = 1;
    private long started, duration;
    double value(long now) {
        if (duration <= 0 || now - started >= duration) return target;
        double t = Math.max(0, (double)(now - started) / duration);
        t = t * t * (3 - 2 * t);
        return from + (target - from) * t;
    }
    void set(double gain) { from = target = clamp(gain); duration = 0; }
    void to(double gain, long nanos, long now) {
        from = value(now); target = clamp(gain); started = now; duration = Math.max(0, nanos);
    }
    boolean done(long now) { return duration <= 0 || now - started >= duration; }
    static double distance(double gain) { return 16.0 * (1.0 - clamp(gain)); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
