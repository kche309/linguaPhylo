package lphy.graphicalModel;

import java.lang.reflect.Array;
import java.util.*;

import static java.util.Collections.max;

/**
 * Created by Alexei Drummond on 18/12/19.
 */
public class IndexedRandomVariable<T> extends RandomVariable<T[]> {

    Map<RangeElement, GenerativeDistribution<T>> distributionMap = new HashMap<>();

    public IndexedRandomVariable(String id, RandomVariable<T> part, RangeElement rangeElement) {
        super(id, null, null);
        setId(id);

        List<Integer> range = Arrays.asList(rangeElement.range());
        int max = max(range);

        value = (T[])Array.newInstance(part.value().getClass(),max+1);

        if (range.size() > 1) {
            throw new IllegalArgumentException("Expected a range of length 1");
        }

        value[range.get(0)] = part.value;

        distributionMap.put(rangeElement, part.getGenerativeDistribution());
    }

//    public Generator<T> getGenerator() {
//        return g;
//    }
//
//    public GenerativeDistribution<T> getGenerativeDistribution() {
//        return g;
//    }

    @Override
    public List<GraphicalModelNode> getInputs() {
        TreeSet set = new TreeSet();
        set.addAll(distributionMap.values());
        List<GraphicalModelNode> list = new ArrayList<>();
        list.addAll(set);
        return list;
    }
}