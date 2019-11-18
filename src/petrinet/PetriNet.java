package petrinet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;


public class PetriNet<T> {
    private boolean fair;

    private final Semaphore readStateMutex = new Semaphore(1, true);
    private ConcurrentMap<T, Integer> currentState;
    private BlockingQueue<ThreadInfo> queue = new LinkedBlockingDeque<>();

    private class ThreadInfo {
        Thread thread;
        Collection<Transition<T>> transitions;
        Semaphore threadMutex;
        Semaphore decisiveMutex;
        Transition<T> chosen;

        ThreadInfo(Thread thread, Collection<Transition<T>> transitions, Semaphore threadMutex, Semaphore decisiveMutex) {
            this.thread = thread;
            this.transitions = transitions;
            this.threadMutex = threadMutex;
            this.decisiveMutex = decisiveMutex;
        }
    }

    private class Decisive implements Runnable {
        @Override
        public void run() {
            int i = 0;
            List<ThreadInfo> threads = new ArrayList<>();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (threads.size() == i) {
                        threads.add(queue.take());
                    }
                    ThreadInfo info = threads.get(i);
                    if (info.decisiveMutex.availablePermits() == 0) { //if thread hasn't interrupted
                        Transition<T> chosenTransition = chooseTransition(info.transitions);
                        if (chosenTransition != null) {
                            threads.remove(i);
                            i = 0;
                            info.chosen = chosenTransition;
                            readStateMutex.acquire();
                            info.threadMutex.release();
                            info.decisiveMutex.acquire();
                            readStateMutex.release();
                        } else {
                            i++;
                        }
                    } else {
                        threads.remove(i);
                    }
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public PetriNet(Map<T, Integer> initial, boolean fair) {
        this.currentState = new ConcurrentHashMap<>(clone(initial));
        this.fair = fair;
        Thread decisive = new Thread(new Decisive());
        decisive.setDaemon(true);
        decisive.setName("Decisive");
        decisive.start();
    }


    public Transition<T> fire(Collection<Transition<T>> transitions) throws InterruptedException {
        Thread current = Thread.currentThread();
        Semaphore threadMutex = new Semaphore(0);
        Semaphore decisiveMutex = new Semaphore(0);
        ThreadInfo info = new ThreadInfo(current, transitions, threadMutex, decisiveMutex);
        try {
            queue.put(info);
            threadMutex.acquire();
            Transition<T> result = info.chosen;
            result.evaluate(currentState);
            decisiveMutex.release();
            return result;
        } catch (InterruptedException e) {
            queue.remove(info);
            info.decisiveMutex.release();
            throw e;
        }
    }


    private Transition<T> chooseTransition(Collection<Transition<T>> transitions) {
        Transition<T> chosenTransition = null;
        for (Transition<T> t : transitions) {
            if (t.isEnabled(currentState)) {
                chosenTransition = t;
                break;
            }
        }
        return chosenTransition;
    }

    public Set<Map<T, Integer>> reachable(Collection<Transition<T>> transitions) {
        Set<Map<T, Integer>> result = new HashSet<>();
        try {
            readStateMutex.acquire();
            Map<T, Integer> init = clone(currentState);
            readStateMutex.release();
            result.add(init);
            for (Transition<T> trans : transitions) {
                if (trans.isEnabled(init))
                    recursiveReachable(transitions, result, trans.evaluate(clone(init)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    private void recursiveReachable(Collection<Transition<T>> transitions, Set<Map<T, Integer>> result, Map<T, Integer> state) {
        if (!result.contains(state)) {
            result.add(state);
            for (Transition<T> trans : transitions) {
                if (trans.isEnabled(state))
                    recursiveReachable(transitions, result, trans.evaluate(clone(state)));
            }
        }
    }

    private Map<T, Integer> clone(Map<T, Integer> original) {
        Map<T, Integer> result = new HashMap<>();
        for (Map.Entry<T, Integer> entry : original.entrySet())
            result.put(entry.getKey(), entry.getValue());
        return result;
    }
}