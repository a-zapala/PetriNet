package petrinet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;


public class PetriNet<T> {
    private boolean fair;
    private final Semaphore write_mutex = new Semaphore(1);

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
                    write_mutex.acquire();

                    if (threads.size() == i) {
                        threads.add(queue.take());
                    }

                    Thread thread = threads.get(i);
                    ThreadInfoElement info = threadsInfo.get(thread);

                    if(info != null) { //if thread hasn't interrupted
                        Transition<T> chosenTransition = chooseTransition(info.transitions);
                        if( chosenTransition != null) {
                            threads.remove(i);
                            if(threadsInfo.computeIfPresent(thread, (k, v) -> { //check again if it's interrupted
                                v.isChosen = true;
                                v.chosen = chosenTransition;
                                v.mutex.release();
                                return v;
                            }) != null ) {
                                i = 0;
                            } else {
                                write_mutex.release();
                            }
                        } else {
                            i++;
                            write_mutex.release();
                        }
                    } else {
                        threads.remove(i);
                        write_mutex.release();
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
        this.decisive.setName("POMOC");
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
            write_mutex.release();
            return result;
        } catch (InterruptedException e) {
            if (threadsInfo.get(current).isChosen)
                write_mutex.release();
            queue.remove(current);
            threadsInfo.remove(current);
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
}