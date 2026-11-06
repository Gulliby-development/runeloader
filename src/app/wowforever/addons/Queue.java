package app.wowforever.addons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Installs, updates and removes the user started, in the order they run, and the last ones that
 * finished. One per process, so a recreated activity still sees work that is running. Plain Java:
 * the ops thread changes it, the screens read it.
 */
final class Queue {
    enum Kind { INSTALL, UPDATE, REMOVE, ZIP }
    enum State { WAITING, RUNNING, DONE, FAILED, CANCELLED }

    /** Finished operations kept for the queue screen. */
    static final int KEEP_FINISHED = 20;

    static final class Op {
        final long id;
        final Kind kind;
        /** The key of the row the user acted on. */
        final String key;
        final String name;
        final String iconUrl;
        /** Every store key this operation writes: the item, and a package's modules. */
        final Set<String> keys = new HashSet<>();
        /** Lowercase folders a removal deletes. */
        final Set<String> folders = new HashSet<>();
        /** Modules that come with a package install or removal. */
        int modules;
        State state = State.WAITING;
        String status = "";
        /** 0..1, or below 0 while the size is unknown. */
        float fraction = -1;
        String error;
        long finishedAt;

        Op(long id, Kind kind, String key, String name, String iconUrl) {
            this.id = id;
            this.kind = kind;
            this.key = key;
            this.name = name;
            this.iconUrl = iconUrl;
            keys.add(key);
        }

        boolean active() { return state == State.WAITING || state == State.RUNNING; }

        boolean finished() { return !active(); }

        void addFolder(String f) { folders.add(f.toLowerCase(Locale.ROOT)); }
    }

    private static final Queue INSTANCE = new Queue();

    static Queue get() { return INSTANCE; }

    private final List<Op> ops = new ArrayList<>();
    private long next = 1;

    synchronized Op add(Kind kind, String key, String name, String iconUrl) {
        Op op = new Op(next++, kind, key, name, iconUrl);
        ops.add(op);
        return op;
    }

    /** The waiting or running operation that writes this key, or null. */
    synchronized Op activeFor(String key) {
        if (key == null) return null;
        for (Op o : ops) if (o.active() && o.keys.contains(key)) return o;
        return null;
    }

    synchronized boolean busy(String key) { return activeFor(key) != null; }

    /** The status of the operation on this key, such as "Queued…" or "Downloading 40%". */
    synchronized String status(String key) {
        Op o = activeFor(key);
        return o == null ? null : o.status;
    }

    /** The waiting or running removal that deletes any of these folders, or null. */
    synchronized Op removing(Collection<String> folders) {
        for (Op o : ops) {
            if (!o.active() || o.kind != Kind.REMOVE) continue;
            for (String f : folders) if (o.folders.contains(f.toLowerCase(Locale.ROOT))) return o;
        }
        return null;
    }

    /** Running first, then waiting in the order they will run, then finished, newest first. */
    synchronized List<Op> snapshot() {
        List<Op> l = new ArrayList<>();
        for (Op o : ops) if (o.state == State.RUNNING) l.add(o);
        for (Op o : ops) if (o.state == State.WAITING) l.add(o);
        List<Op> done = new ArrayList<>();
        for (Op o : ops) if (o.finished()) done.add(o);
        done.sort((a, b) -> Long.compare(b.finishedAt, a.finishedAt) != 0 ? Long.compare(b.finishedAt, a.finishedAt) : Long.compare(b.id, a.id));
        l.addAll(done);
        return l;
    }

    synchronized int activeCount() {
        int n = 0;
        for (Op o : ops) if (o.active()) n++;
        return n;
    }

    synchronized int finishedCount() { return ops.size() - activeCount(); }

    /** The operation the progress card shows: the running one, else the next to run. */
    synchronized Op current() {
        for (Op o : ops) if (o.state == State.RUNNING) return o;
        for (Op o : ops) if (o.state == State.WAITING) return o;
        return null;
    }

    /** Called by the ops thread before the work; false when the user cancelled it while it waited. */
    synchronized boolean start(Op o) {
        if (o.state != State.WAITING) return false;
        o.state = State.RUNNING;
        return true;
    }

    /** Only an operation that has not started can be cancelled: a running one may be half written. */
    synchronized boolean cancel(Op o) {
        if (o.state != State.WAITING) return false;
        o.state = State.CANCELLED;
        o.finishedAt = System.currentTimeMillis();
        trim();
        return true;
    }

    synchronized void progress(Op o, String status, float fraction) {
        o.status = status;
        o.fraction = fraction;
    }

    synchronized void finish(Op o, String error) {
        o.state = error == null ? State.DONE : State.FAILED;
        o.error = error;
        o.fraction = error == null ? 1 : o.fraction;
        o.finishedAt = System.currentTimeMillis();
        trim();
    }

    synchronized void clearFinished() {
        ops.removeIf(Op::finished);
    }

    private void trim() {
        int extra = finishedCount() - KEEP_FINISHED;
        for (int i = 0; i < ops.size() && extra > 0; ) {
            if (ops.get(i).finished()) { ops.remove(i); extra--; } else i++;
        }
    }
}
