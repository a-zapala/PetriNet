package multiplicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import petrinet.PetriNet;
import petrinet.Transition;

public class Main {
    private static final int THREAD_NUMBER = 4;

    private enum Place {
        A, B1, B2, B3, B1_T, B2_T, ACC, RES, END
    }

    private static Collection<Transition<Place>> createCommonTransition() {
        Collection<Transition<Place>> result = new ArrayList<>();

        //copying from B1
        Map<Place, Integer> input = Map.ofEntries(
                Map.entry(Place.B1, 1),
                Map.entry(Place.B1_T, 1)
        );
        Map<Place, Integer> output = Map.ofEntries(
                Map.entry(Place.B1_T, 1),
                Map.entry(Place.B2, 1),
                Map.entry(Place.B3, 1)
        );
        Collection<Place> reset = Collections.emptyList();
        Collection<Place> inhibitor = Collections.emptyList();
        result.add(new Transition<>(input, reset, inhibitor, output));

        //copying from B2
        input = Map.ofEntries(
                Map.entry(Place.B2, 1),
                Map.entry(Place.B2_T, 1)
        );
        output = Map.ofEntries(
                Map.entry(Place.B2_T, 1),
                Map.entry(Place.B1, 1),
                Map.entry(Place.B3, 1)
        );
        reset = Collections.emptyList();
        inhibitor = Collections.emptyList();
        result.add(new Transition<>(input, reset, inhibitor, output));

        //change to ACC from B1
        input = Map.ofEntries(
                Map.entry(Place.A, 1),
                Map.entry(Place.B1_T, 1)
        );
        output = Map.ofEntries(
                Map.entry(Place.ACC, 1)
        );
        reset = Collections.emptyList();
        inhibitor = Collections.singletonList(Place.B1);
        result.add(new Transition<>(input, reset, inhibitor, output));

        //change to ACC from B2
        input = Map.ofEntries(
                Map.entry(Place.A, 1),
                Map.entry(Place.B2_T, 1)
        );
        output = Map.ofEntries(
                Map.entry(Place.ACC, 1)
        );
        reset = Collections.emptyList();
        inhibitor = Collections.singletonList(Place.B2);
        result.add(new Transition<>(input, reset, inhibitor, output));

        //accumulating
        input = Map.ofEntries(
                Map.entry(Place.B3, 1),
                Map.entry(Place.ACC, 1)
        );
        output = Map.ofEntries(
                Map.entry(Place.ACC, 1),
                Map.entry(Place.RES, 1)
        );
        reset = Collections.emptyList();
        inhibitor = Collections.emptyList();
        result.add(new Transition<>(input, reset, inhibitor, output));

        //from ACC to B2
        input = Map.ofEntries(
                Map.entry(Place.A, 1),
                Map.entry(Place.ACC, 1)
        );
        output = Map.ofEntries(
                Map.entry(Place.B2_T, 1),
                Map.entry(Place.A, 1)
        );
        reset = Collections.emptyList();
        inhibitor = Arrays.asList(Place.B1, Place.B3);
        result.add(new Transition<>(input, reset, inhibitor, output));

        //from ACC to B1
        input = Map.ofEntries(
                Map.entry(Place.A, 1),
                Map.entry(Place.ACC, 1)
        );
        output = Map.ofEntries(
                Map.entry(Place.B1_T, 1),
                Map.entry(Place.A, 1)
        );
        reset = Collections.emptyList();
        inhibitor = Arrays.asList(Place.B2, Place.B3);
        result.add(new Transition<>(input, reset, inhibitor, output));

        return result;
    }

    private static Collection<Transition<Place>> createEndTransition() {
        Collection<Transition<Place>> result = new ArrayList<>();

        Map<Place, Integer> input = Collections.emptyMap();
        Map<Place, Integer> output = Map.ofEntries(
                Map.entry(Place.END, 1)
        );
        Collection<Place> reset = Collections.emptyList();
        Collection<Place> inhibitor = Arrays.asList(Place.A, Place.B3, Place.END);
        result.add(new Transition<>(input, reset, inhibitor, output));

        return result;
    }

    private static PetriNet<Place> createMultiplicatorPetriNet(int a, int b) {
        Map<Place, Integer> map = Map.ofEntries(
                Map.entry(Place.A, a),
                Map.entry(Place.B1, b),
                Map.entry(Place.B1_T, 1)
        );
        return new PetriNet<>(map, true);
    }

    private static class Executor implements Runnable {

        private Collection<Transition<Place>> transitions;
        private PetriNet<Place> net;
        private int number;
        private int numberOfFire;

        Executor(Collection<Transition<Place>> transitions, PetriNet<Place> net, int number) {
            this.transitions = transitions;
            this.net = net;
            this.number = number;
            this.numberOfFire = 0;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    net.fire(transitions);
                    numberOfFire++;
                }
            } catch (InterruptedException e) {
                System.out.println("Thread number " + number + " interrupted");
            }
            System.out.println("Number of fires " + numberOfFire);
        }
    }


    public static void main(String[] args) throws InterruptedException {
        int a, b;
        Scanner in = new Scanner(System.in);
        a = in.nextInt();
        b = in.nextInt();
        PetriNet<Place> net = createMultiplicatorPetriNet(a, b);
        Collection<Transition<Place>> commonTransition = createCommonTransition();
        Collection<Transition<Place>> endTransition = createEndTransition();


        ThreadGroup threadGroup = new ThreadGroup("group");
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREAD_NUMBER; i++) {
            Thread thread = new Thread(threadGroup, new Executor(commonTransition, net, i));
            threads.add(thread);
            thread.start();
        }

        Thread.sleep(200);
        for(int i = THREAD_NUMBER/2; i < THREAD_NUMBER; i++){
            threads.get(i).interrupt();
        }

        net.fire(endTransition);
        Set<Map<Place,Integer>> lastState = net.reachable(endTransition);
        assert lastState.equals(net.reachable(commonTransition));
        assert lastState.size() == 1;
        System.out.println(lastState.iterator().next().get(Place.RES));
        threadGroup.interrupt();

    }
}
