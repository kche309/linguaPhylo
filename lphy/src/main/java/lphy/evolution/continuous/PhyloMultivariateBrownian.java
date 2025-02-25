package lphy.evolution.continuous;

import lphy.core.StringDoubleArrayMap;
import lphy.evolution.alignment.ContinuousCharacterData;
import lphy.evolution.tree.TimeTree;
import lphy.evolution.tree.TimeTreeNode;
import lphy.core.distributions.Utils;
import lphy.graphicalModel.*;
import lphy.graphicalModel.types.DoubleArrayValue;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by Alexei Drummond on 2/02/20.
 */
public class PhyloMultivariateBrownian implements GenerativeDistribution<ContinuousCharacterData> {

    Value<TimeTree> tree;
    Value<Double[][]> diffusionMatrix;
    Value<Double[]> y0;
    RandomGenerator random;

    public static final String treeParamName = "tree";
    public static final String diffusionMatrixParamName = "diffusionMatrix";
    public static final String y0ParamName = "y0";

    public PhyloMultivariateBrownian(@ParameterInfo(name = treeParamName, description = "the time tree.") Value<TimeTree> tree,
                                     @ParameterInfo(name = diffusionMatrixParamName, description = "the multivariate diffusion rates.") Value<Double[][]> diffusionRate,
                                     @ParameterInfo(name = y0ParamName, description = "the value of multivariate traits at the root.") Value<Double[]> y0) {

        this.tree = tree;
        this.diffusionMatrix = diffusionRate;
        this.y0 = y0;
        this.random = Utils.getRandom();
    }

    public RandomVariable<ContinuousCharacterData> sample() {

        SortedMap<String, Integer> idMap = new TreeMap<>();
        fillIdMap(tree.value().getRoot(), idMap); // populate idMap in place

        // populate tipValues (key = species name, value = trait values)
        Map<String, Double[]> tipValues = new StringDoubleArrayMap(); // StringDoubleArrayMap extends Java API's TreeMap
        fillValuesTraversingTree(tree.value().getRoot(), y0, tipValues, diffusionMatrix.value(), idMap);

        // put tipValues inside contData
        Double[][] contData = new Double[tree.value().n()][y0.value().length];
        for (Map.Entry<String, Double[]> entry : tipValues.entrySet()) {
            contData[tree.value().getTaxa().indexOfTaxon(entry.getKey())] = entry.getValue();
        }

        return new RandomVariable<>("x", new ContinuousCharacterData(tree.value().getTaxa(), contData), this);
    }

    /*
     * Side-effect: populates idMap in place
     *
     * key = the (String) name of a species
     * value = an integer associated to that species
     */
    private void fillIdMap(TimeTreeNode node, SortedMap<String, Integer> idMap) {
        if (node.isLeaf()) {
            Integer i = idMap.get(node.getId()); // will be null the first time this is done because idMap is empty!

            if (i == null) {
                int nextValue = 0; // if looking at first leaf, it's mapped to value 0

                for (Integer j : idMap.values()) {
                    if (j >= nextValue)
                        nextValue = j + 1; // if looking at second or later species, we add +1 to integer that will be its value
                }

                idMap.put(node.getId(), nextValue);
            }
        }

        // recur
        else {
            for (TimeTreeNode child : node.getChildren()) {
                fillIdMap(child, idMap);
            }
        }
    }

    /*
     * Side-effect: populates tipValues in place
     *
     * tipValues is a map (key = species name, value = trait values)
     */
    private void fillValuesTraversingTree(TimeTreeNode node, Value<Double[]> nodeState, Map<String, Double[]> tipValues, Double[][] diffusionMatrix, Map<String, Integer> idMap) {
        if (node.isLeaf()) {
            tipValues.put(node.getId(), nodeState.value()); // finished traversing tree, we have our tip values
        }

        // recur
        else {
            for (TimeTreeNode child : node.getChildren()) {
                double branchLength = node.getAge() - child.getAge();
                Double[] newIntNodeState = getSampleFromNewMVN(nodeState.value(), diffusionMatrix, branchLength); // MVN sampling here
                DoubleArrayValue newIntNodeStateValue = new DoubleArrayValue(null, newIntNodeState);

                fillValuesTraversingTree(child, newIntNodeStateValue, tipValues, diffusionMatrix, idMap);
            }
        }
    }

    protected Double[] handleBoundaries(double[] rawValues) {
        /* original code */
//        Double[] objValues =  new Double[rawValues.length];
//        for (int i = 0; i < rawValues.length; i++) { objValues[i] = rawValues[i]; }
//
//        return objValues;

        return ArrayUtils.toObject(rawValues);
    }

    /*
     * Getters and setters
     */

    /*
     * Returns a random sample from a (new) MVN distribution
     * defined from:
     * (1) a previous (MVN) mean and var-cov, and
     * (2) some waiting time during which diffusion happens (branchLength);
     * at the end of this waiting time, we draw
     */
    Double[] getSampleFromNewMVN(Double[] oldValue, Double[][] diffusionMatrix, double branchLength) {
        // initializing moment containers of MVN
        double[] means = new double[oldValue.length];
        double[][] covariances = new double[diffusionMatrix.length][diffusionMatrix[0].length];

        // populating moment containers
        for (int i = 0; i < covariances.length; i++) {
            means[i] = oldValue[i]; // mean remains the same under BM

            /*
             * variances and co-variances must be updated (they increase with time
             * as diffusion is happening), which is done here by multiplying
             * previous var-covars by branchLength
             */
            for (int j = 0; j < covariances.length; j++) {
                covariances[i][j] = diffusionMatrix[i][j] * branchLength;
            }
        }

        MultivariateNormalDistribution mvn = new MultivariateNormalDistribution(means, covariances);

        return handleBoundaries(mvn.sample());
    }

    // getParams is in the Generator interface
    @Override
    public Map<String, Value> getParams() {
        return new TreeMap<>() {{
            put(treeParamName, tree);
            put(diffusionMatrixParamName, diffusionMatrix);
            put(y0ParamName, y0);
        }};
    }

    // setParam is in the Generator interface
    @Override
    public void setParam(String paramName, Value value) {
        switch (paramName) {
            case treeParamName:
                tree = value;
                break;
            case diffusionMatrixParamName:
                diffusionMatrix = value;
                break;
            case y0ParamName:
                y0 = value;
                break;
            default:
                throw new RuntimeException("Unrecognised parameter name: " + paramName);
        }
    }
}
