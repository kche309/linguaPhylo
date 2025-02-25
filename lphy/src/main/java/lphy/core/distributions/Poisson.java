package lphy.core.distributions;

import lphy.graphicalModel.*;
import org.apache.commons.math3.distribution.PoissonDistribution;

import java.util.Map;
import java.util.TreeMap;

import static lphy.graphicalModel.ValueUtils.doubleValue;

/**
 * Created by Alexei Drummond on 18/12/19.
 */
public class Poisson implements GenerativeDistribution1D<Integer> {

    private static final String lambdaParamName = "lambda";
    private static final String offsetParamName = "offset";
    private static final String minParamName = "min";
    private static final String maxParamName = "max";
    private Value<Number> lambda;
    private Value<Integer> min;
    private Value<Integer> max;
    private Value<Integer> offset;

    static final int MAX_TRIES = 10000;

    public Poisson(@ParameterInfo(name=lambdaParamName, description="the expected number of events.") Value<Number> lambda,
                   @ParameterInfo(name=offsetParamName, optional = true, description = "optional parameter to add a constant to the returned result. default is 0") Value<Integer> offset,
                   @ParameterInfo(name=minParamName, optional = true, description = "optional parameter to specify a condition that the number of events must be greater than or equal to this mininum") Value<Integer> min,
                   @ParameterInfo(name=maxParamName, optional = true, description = "optional parameter to specify a condition that the number of events must be less than or equal to this maximum") Value<Integer> max) {
        this.lambda = lambda;
        this.min = min;
        this.max = max;
        this.offset = offset;
    }

    @GeneratorInfo(name="Poisson", description="The probability distribution of the number of events when the expected number of events is lambda, supported on the set { 0, 1, 2, 3, ... }.")
    public RandomVariable<Integer> sample() {

        PoissonDistribution poisson = new PoissonDistribution(doubleValue(lambda));

        int minimum = min();
        int maximum = max();

        int val = -1;
        int count = 0;
        while (val < minimum || val > maximum) {
            val = poisson.sample() + C();
            count += 1;
            if (count > MAX_TRIES) {
                throw new RuntimeException("Failed to draw conditional Poisson random variable after " + MAX_TRIES + " attempts.");
            }
        }


        return new RandomVariable<>(null, val, this);
    }

    private int C() {
        int C = 0;
        if (offset != null) {
            C = offset.value();
        }
        return C;
    }

    private int min() {
        if (min != null) return min.value();
        return 0;
    }

    private int max() {
        if (max != null) return max.value();
        return Integer.MAX_VALUE;
    }

    public double density(Integer i) {
        PoissonDistribution poisson = new PoissonDistribution(doubleValue(lambda));
        if (i < min()) return 0.0;
        if (i > max()) return 0.0;
        return poisson.probability(i-C());
    }

    @Override
    public Map<String,Value> getParams() {
        return new TreeMap<>() {{
            put(lambdaParamName, lambda);
            if (min != null) put(minParamName, min);
            if (max != null) put(maxParamName, max);
            if (offset != null) put(offsetParamName, offset);
        }};    }

    @Override
    public void setParam(String paramName, Value value) {
        switch (paramName) {
            case lambdaParamName:
                lambda = value;
                break;
            case minParamName:
                min = value;
                break;
            case maxParamName:
                max = value;
                break;
            case offsetParamName:
                offset = value;
                break;
            default:
                throw new RuntimeException("The valid parameter names are " + lambdaParamName + ", " + minParamName + ", " + maxParamName + " and " + offsetParamName);
        }
    }

    public void setLambda(double p) {
        this.lambda.setValue(p);
    }

    public String toString() {
        return getName();
    }

    private static final Integer[] domainBounds = {0, Integer.MAX_VALUE};
    public Integer[] getDomainBounds() {
        return domainBounds;
    }

    public Value<Number> getLambda() {
        return lambda;
    }

    public Value<Integer> getMin() {
        return min;
    }

    public Value<Integer> getMax() {
        return max;
    }

    public Value<Integer> getOffset() {
        return offset;
    }

}
