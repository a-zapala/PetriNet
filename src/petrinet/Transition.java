package petrinet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Transition<T> {

    private Map<T,Integer> input; //all positive integer
    private Map<T,Integer> output; //all positive integer
    private Collection<T> reset;
    private Collection<T> inhibitor;


    public Transition(Map<T, Integer> input, Collection<T> reset, Collection<T> inhibitor, Map<T, Integer> output) {
        this.input = input;
        this.output = output;
        this.reset = reset;
        this.inhibitor = inhibitor;
    }

    public boolean isEnabled(ConcurrentMap<T,Integer> state) {
        for (T edge: input.keySet()) {
            if(input.get(edge) > state.getOrDefault(edge,0)) {
                return false;
            }
        }

        for(T edge : inhibitor) {
            if(state.containsKey(edge)) {
                return false;
            }
        }
        return true;
    }

    void evaluate(ConcurrentMap<T, Integer> state) {
        for (T edge: input.keySet()) {
            state.put(edge, state.get(edge) - input.get(edge));
        }

        for (T edge: reset) {
            state.remove(edge);
        }

        for(T edge : output.keySet()) {
            state.compute(edge, (k,v) -> (v == null) ? output.get(edge) : v + output.get(edge));
        }
    }


}