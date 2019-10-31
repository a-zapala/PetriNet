package petrinet;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

public class PetriNet<T> {
    private boolean fair;
    private ConcurrentMap<T, Integer> current_state;

    private final Semaphore reading = new Semaphore(1, true);
    private final Semaphore writing = new Semaphore(1, true);
    private int inside_reading = 0;

    private Barrier barrier;
    private List<AwaitingThread> awaiting_threads = Collections.synchronizedList(new ArrayList<>());
    private Thread chosen_thread;

    static class SortAwaitingThreads implements Comparator<AwaitingThread> {
        @Override
        public int compare(AwaitingThread t1, AwaitingThread t2) {
            if (!t1.want_write) {
                return 1;
            } else if (!t2.want_write) {
                return -1;
            } else {
                return (int) (t1.start_time - t2.start_time); //maybe dangerous cast
            }

        }
    }

    private class Barrier extends CyclicBarrier {
        Barrier(int number) {
            super(number, new Runnable() {
                @Override
                public void run() {
                   awaiting_threads.sort(new SortAwaitingThreads());
                   chosen_thread = awaiting_threads.get(0).thread;
                   awaiting_threads.clear();
                }
            });
        }
    }

    private static class AwaitingThread {
        Thread thread;
        boolean want_write;
        long start_time;

        AwaitingThread(Thread thread, boolean want_write, long start_time) {
            this.thread = thread;
            this.want_write = want_write;
            this.start_time = start_time;
        }
    }


    public PetriNet(Map<T, Integer> initial, boolean fair) {
        this.current_state = new ConcurrentHashMap<>(initial);
        this.fair = fair;
    }

//    public Set<Map<T, Integer>> reachable(Collection<Transition<T>> transitions) {
//        ConcurrentMap<T, Integer> init = new ConcurrentHashMap<>(<T, Integer>(current_state);
//        Set<Map<T, Integer>> result = new HashSet<>();
//        result.add(init);
//        for (Transition<T> t : transitions) {
//            if(t.isEnabled(init));
//            rec_reachable(transitions, result, t.evaluate(init));
//        }
//        return result;
//    }

    private void rec_reachable(Collection<Transition<T>> transitions, Set<Map<T, Integer>> result, Map<T, Integer> state) {
        if (!result.contains(state)) {
            result.add(state);
            for (Transition<T> t : transitions) {
                rec_reachable(transitions, result, t.evaluate(state));
            }
        }
    }

    public Transition<T> fire(Collection<Transition<T>> transitions) throws InterruptedException {
        long start_time = System.currentTimeMillis();
        Thread thread = Thread.currentThread();

        for(;;) {
            join_to_reading();
            Transition<T> chosen_transition = choose_transition(transitions);
            boolean want_to_write = chosen_transition != null;
            join_to_writing();
            awaiting_threads.add(new AwaitingThread(thread, want_to_write, start_time));
            try {
                barrier.await();
            } catch (BrokenBarrierException e) {
                throw new InterruptedException();
            }
            if (thread == chosen_thread) {
                if (want_to_write) {
                    chosen_transition.evaluate(current_state);
                }
                reading.release();
                return chosen_transition;
            }
        }
    }

    private void join_to_reading() throws InterruptedException {
        reading.acquire();
        inside_reading++;
        reading.release();
    }

    private Transition<T> choose_transition(Collection<Transition<T>> transitions) {
        Transition<T> chosen_transition = null;
        for (Transition<T> t : transitions) {
            if (t.isEnabled(current_state)) {
                chosen_transition = t;
                break;
            }
        }
        return chosen_transition;
    }

    private void join_to_writing() throws InterruptedException {
        writing.acquire();
        if (barrier == null) {
            reading.acquire(); //first awaiting to write, so block reading
            barrier = new Barrier(inside_reading);
        }
        writing.release();
    }


}