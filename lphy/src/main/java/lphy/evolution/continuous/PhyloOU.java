package lphy.evolution.continuous;

import lphy.evolution.alignment.ContinuousCharacterData;
import lphy.evolution.tree.TimeTree;
import lphy.graphicalModel.GeneratorInfo;
import lphy.graphicalModel.ParameterInfo;
import lphy.graphicalModel.RandomVariable;
import lphy.graphicalModel.Value;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by Alexei Drummond on 2/02/20.
 */
public class PhyloOU extends PhyloBrownian {

    protected Value<Double> theta;
    protected Value<Double> alpha;
    protected Value<Double[]> branchThetas;

    public static final String thetaParamName = "theta";
    public static final String branchThetasParamName = "branchThetas";
    public static final String alphaParamName = "alpha";

    public PhyloOU(@ParameterInfo(name = treeParamName, description = "the time tree.") Value<TimeTree> tree,
                   @ParameterInfo(name = diffRateParamName, description = "the variance of the underlying Brownian process. This is not the equilibrium variance of the OU process.") Value<Double> variance,
                   @ParameterInfo(name = thetaParamName, description = "the 'optimal' value that the long-term process is centered around.", optional = true) Value<Double> theta,
                   @ParameterInfo(name = alphaParamName, description = "the drift term that determines the rate of drift towards the optimal value.") Value<Double> alpha,
                   @ParameterInfo(name = y0ParamName, description = "the value of continuous trait at the root.") Value<Double> y0,
                   @ParameterInfo(name = branchThetasParamName, description = "the 'optimal' value for each branch in the tree.", optional = true) Value<Double[]> branchThetas
    ) {

        super(tree, variance, y0);

        this.theta = theta;
        this.branchThetas = branchThetas;
        this.alpha = alpha;
    }

    @Override
    public SortedMap<String, Value> getParams() {
        SortedMap<String, Value> map = new TreeMap<>();
        map.put(treeParamName, tree);
        map.put(diffRateParamName, diffusionRate);
        if (theta != null) map.put(thetaParamName, theta);
        map.put(alphaParamName, alpha);
        map.put(y0ParamName, y0);
        if (branchThetas != null) map.put(branchThetasParamName, branchThetas);
        return map;
    }

    @Override
    public void setParam(String paramName, Value value) {
        if (paramName.equals(treeParamName)) tree = value;
        else if (paramName.equals(diffRateParamName)) diffusionRate = value;
        else if (paramName.equals(thetaParamName)) theta = value;
        else if (paramName.equals(branchThetasParamName)) branchThetas = value;
        else if (paramName.equals(alphaParamName)) alpha = value;
        else if (paramName.equals(y0ParamName)) y0 = value;
        else throw new RuntimeException("Unrecognised parameter name: " + paramName);
    }

    protected double sampleNewState(double initialState, double time, int nodeIndex) {

        double th;
        if (theta != null) {
            th = theta.value();
        } else {
            th = branchThetas.value()[nodeIndex];
        }

        double a = alpha.value();

        double v = diffusionRate.value() / (2 * a);

        double weight = Math.exp(-a * time);

        double mean = (1.0 - weight) * th + weight * initialState;

        double variance = v * (1.0 - Math.exp(-2.0 * a * time));

        NormalDistribution distribution = new NormalDistribution(mean, Math.sqrt(variance));
        return handleBoundaries(distribution.sample());
    }

    @GeneratorInfo(name = "PhyloOU",
            verbClause = "is assumed to have evolved under",
            narrativeName = "phylogenetic Ornstein-Ulhenbeck process",
            description = "The phylogenetic Ornstein-Ulhenbeck distribution. A continous trait is simulated for every leaf node, and every direct ancestor node with an id.")
    public RandomVariable<ContinuousCharacterData> sample() {
        return super.sample();
    }
}
