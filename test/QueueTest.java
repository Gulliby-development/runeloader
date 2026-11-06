package app.wowforever.addons;

import java.util.Arrays;
import java.util.List;

public class QueueTest {
    static void check(boolean ok, String what) { if (!ok) throw new RuntimeException("FAIL: " + what); System.out.println("ok  " + what); }

    public static void main(String[] a) {
        Queue q = new Queue();
        Queue.Op install = q.add(Queue.Kind.INSTALL, "github:a/Pack", "Pack", null);
        install.keys.add("github:a/Pack#Wheel");
        Queue.Op remove = q.add(Queue.Kind.REMOVE, "wowi:1", "Questie", null);
        remove.addFolder("Questie");
        Queue.Op later = q.add(Queue.Kind.UPDATE, "wowi:2", "Bagnon", null);

        check(q.busy("github:a/Pack#Wheel"), "a package install marks its modules busy");
        check(!q.busy("github:a/Other"), "other keys are free");
        check(q.removing(Arrays.asList("QUESTIE")) == remove, "removal found by folder, case ignored");
        check(q.removing(Arrays.asList("Bagnon")) == null, "folders not removed are not marked");
        check(q.current() == install && q.activeCount() == 3, "the first waiting operation is current");

        check(q.start(install), "start a waiting operation");
        check(q.cancel(later) && !q.busy("wowi:2"), "a waiting operation can be cancelled");
        check(!q.cancel(install), "a running operation cannot be cancelled");
        check(!q.start(later), "a cancelled operation does not start");

        q.finish(install, null);
        check(install.state == Queue.State.DONE && !q.busy("github:a/Pack"), "a finished install frees its keys");
        check(q.current() == remove, "the next waiting operation becomes current");
        q.start(remove);
        q.finish(remove, "disk full");
        check(remove.state == Queue.State.FAILED && "disk full".equals(remove.error), "a failure keeps its error");

        List<Queue.Op> s = q.snapshot();
        check(s.size() == 3 && s.get(0).finished(), "finished operations stay listed");
        q.clearFinished();
        check(q.snapshot().isEmpty(), "clear removes finished operations");

        for (int i = 0; i < Queue.KEEP_FINISHED + 5; i++) {
            Queue.Op o = q.add(Queue.Kind.INSTALL, "k" + i, "n" + i, null);
            q.start(o);
            q.finish(o, null);
        }
        check(q.finishedCount() == Queue.KEEP_FINISHED, "only the last finished operations are kept");
        Queue.Op w = q.add(Queue.Kind.INSTALL, "w", "w", null);
        check(q.snapshot().get(0) == w, "waiting operations come before finished ones");
        System.out.println("all queue tests passed");
    }
}
