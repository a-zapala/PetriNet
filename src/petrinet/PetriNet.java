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

    private final Semaphore decisiveMutex = new Semaphore(1);
    private ConcurrentMap<T, Integer> currentState;
    private BlockingQueue<Thread> queue = new LinkedBlockingDeque<>();
    private ConcurrentMap<Thread, ThreadInfoElement> threadsInfo = new ConcurrentHashMap<>();

    private class ThreadInfoElement {
        Collection<Transition<T>> transitions;
        Semaphore mutex;
        Transition<T> chosen;
        boolean isChosen;

        ThreadInfoElement(Collection<Transition<T>> transitions, Semaphore mutex) {
            this.transitions = transitions;
            this.mutex = mutex;
        }
    }


    private Thread decisive;

    private class Decisive implements Runnable {
        @Override
        public void run() {
            int i = 0;
            List<Thread> threads = new ArrayList<>();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    decisiveMutex.acquire();

                    if (threads.size() == i) {
                        threads.add(queue.take());
                    }

                    Thread thread = threads.get(i);
                    ThreadInfoElement info = threadsInfo.get(thread);

                    if (info != null) { //if thread hasn't interrupted
                        Transition<T> chosenTransition = chooseTransition(info.transitions); //TODO synchronise
                        if (chosenTransition != null) {
                            threads.remove(i);
                            if (threadsInfo.computeIfPresent(thread, (k, v) -> { //check again if it's interrupted
                                v.isChosen = true;
                                v.chosen = chosenTransition;
                                v.mutex.release();
                                return v;
                            }) != null) {
                                i = 0;
                            } else {
                                decisiveMutex.release();
                            }
                        } else {
                            i++;
                            decisiveMutex.release();
                        }
                    } else {
                        threads.remove(i);
                        decisiveMutex.release();
                    }
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public PetriNet(Map<T, Integer> initial, boolean fair) {
        this.currentState = new ConcurrentHashMap<>(initial);
        this.fair = fair;
        this.decisive = new Thread(new Decisive());
        this.decisive.setDaemon(true);
        this.decisive.setName("Decisive");
        decisive.start();
    }


    public Transition<T> fire(Collection<Transition<T>> transitions) throws InterruptedException {
        Thread current = Thread.currentThread();
        assert (!threadsInfo.containsKey(current) && !queue.contains(current));
        Semaphore mutex = new Semaphore(0);
        threadsInfo.put(current, new ThreadInfoElement(transitions, mutex));

        try {
            queue.put(current);
            mutex.acquire();
            Transition<T> result = threadsInfo.get(current).chosen;
            threadsInfo.remove(current);
            queue.remove(current);
            result.evaluate(currentState);
            decisiveMutex.release();
            return result;
        } catch (InterruptedException e) {
            queue.remove(current);
            ThreadInfoElement info = threadsInfo.remove(current);
            if (info.isChosen)
                decisiveMutex.release();
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
        Map<T, Integer> init = clone(currentState); //TODO lock on this state ask Zaroda
        Set<Map<T, Integer>> result = new HashSet<>();
        result.add(init);
        for (Transition<T> trans : transitions) {
            if (trans.isEnabled(init))
                recursiveReachable(transitions, result, trans.evaluate(clone(init)));
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