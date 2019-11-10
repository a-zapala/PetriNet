package alternator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import petrinet.PetriNet;
import petrinet.Transition;

public class Main {

    private static final long SIMULATION_TIME = 30000;

    private static List<Place> processes = new ArrayList<>(Arrays.asList(Place.P1, Place.P2, Place.P3));
    private static List<Place> histories = new ArrayList<>(Arrays.asList(Place.H1, Place.H2, Place.H3));

    private enum Place {
        P1, P2, P3, START, EXE, H1, H2, H3
    }

    private static Collection<Transition<Place>> createTransitionForProcess(int processNumber) {
        Collection<Transition<Place>> result = new ArrayList<>();

        Map<Place, Integer> input1 = new HashMap<>();
        Map<Place, Integer> output1 = new HashMap<>();
        Collection<Place> reset1 = new ArrayList<>();
        Collection<Place> inhibitor1 = Collections.singleton(histories.get(processNumber));

        input1.put(processes.get(processNumber), 1);
        input1.put(Place.START, 1);
        output1.put(Place.EXE, 1);
        output1.put(histories.get(processNumber), 1);
        reset1.add(histories.get((processNumber + 1) % 3));
        reset1.add(histories.get((processNumber + 2) % 3));

        Map<Place, Integer> input2 = new HashMap<>();
        Map<Place, Integer> output2 = new HashMap<>();
        Collection<Place> reset2 = new ArrayList<>();
        Collection<Place> inhibitor2 = Collections.singleton(processes.get(processNumber));
        input2.put(Place.EXE, 1);
        output2.put(processes.get(processNumber), 1);
        output2.put(Place.START, 1);

        result.add(new Transition<>(input1, reset1, inhibitor1, output1));
        result.add(new Transition<>(input2, reset2, inhibitor2, output2));
        return result;
    }

    private static PetriNet<Place> createAlternatorPetriNet() {
        HashMap<Place, Integer> map = new HashMap<>();
        map.put(Place.P1, 1);
        map.put(Place.P2, 1);
        map.put(Place.P3, 1);
        map.put(Place.START, 1);

        return new PetriNet<>(map, true);
    }

    private static class ProcessSimulator implements Runnable {

        private Collection<Transition<Place>> transitions;
        private PetriNet<Place> net;
        private String name;

        public ProcessSimulator(Collection<Transition<Place>> transitions, PetriNet<Place> net, String name) {
            this.transitions = transitions;
            this.net = net;
            this.name = name;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    net.fire(transitions);
                    System.out.print(name + ".");
                    System.out.print(name + ".");
                    net.fire(transitions);
                }
            } catch (InterruptedException e) {
                System.out.println("Thread " + name + " interrupted");
            }
        }
    }


    public static void main(String[] args) throws InterruptedException {
        PetriNet<Place> net = createAlternatorPetriNet();
        Collection<Transition<Place>> transitionProcessA = createTransitionForProcess(0);
        Collection<Transition<Place>> transitionProcessB = createTransitionForProcess(1);
        Collection<Transition<Place>> transitionProcessC = createTransitionForProcess(2);

        Collection<Transition<Place>> transitions = new ArrayList<>();
        transitions.addAll(transitionProcessA);
        transitions.addAll(transitionProcessB);
        transitions.addAll(transitionProcessC);

        Set<Map<Place, Integer>> before = net.reachable(transitions);

        System.out.println("Number of marking:" + before.size());
        for (Map<Place, Integer> state : before) {
            if (state.getOrDefault(Place.EXE, 0) > 1) {
                System.out.println("State doesn't meet the safety conditions!");
                return;
            }
        }

        Thread threadA = new Thread(new ProcessSimulator(transitionProcessA, net, "A"));
        Thread threadB = new Thread(new ProcessSimulator(transitionProcessB, net, "B"));
        Thread threadC = new Thread(new ProcessSimulator(transitionProcessC, net, "C"));
        threadA.setName("A");
        threadB.setName("B");
        threadC.setName("C");

        threadA.start();
        threadB.start();
        threadC.start();
        Thread.sleep(SIMULATION_TIME);
        threadA.interrupt();
        threadB.interrupt();
        threadC.interrupt();
    }
}
