package lphy.core.functions;

import lphy.graphicalModel.DeterministicFunction;
import lphy.graphicalModel.GeneratorInfo;
import lphy.graphicalModel.ParameterInfo;
import lphy.graphicalModel.Value;
import lphy.graphicalModel.types.DoubleArray2DValue;

/**
 * Created by Alexei Drummond on 2/02/20.
 */
public class MigrationMatrix extends DeterministicFunction<Double[][]> {

    public static final String thetaParamName = "theta";
    public static final String mParamName = "m";

    public MigrationMatrix(@ParameterInfo(name = thetaParamName, description = "the population sizes.") Value<Double[]> popSizes,
                           @ParameterInfo(name = mParamName, description = "the migration rates between each pair of demes (row-major order minus diagonals).") Value<Double[]> rates) {

        int numPops = popSizes.value().length;
        int numRates = numPops * numPops - numPops;

        if (rates.value().length != numRates)
            throw new IllegalArgumentException("There must be " + numRates + " migration rates for " + numPops + " demes.");

        setParam(thetaParamName, popSizes);
        setParam(mParamName, rates);
    }


    @GeneratorInfo(name = "migrationMatrix", description = "This function constructs the population process rate matrix. Diagonals are the population sizes, off-diagonals are populated with the migration rate from pop i to pop j (backwards in time in units of expected migrants per generation).")
    public Value<Double[][]> apply() {
        Value<Double[]> rates = getParams().get(mParamName);
        Value<Double[]> popSizes = getParams().get(thetaParamName);
        return new DoubleArray2DValue( migrationMatrix(popSizes.value(), rates.value()), this);
    }

    private Double[][] migrationMatrix(Double[] popSizes, Double[] rates) {

        int numDemes = popSizes.length;

        Double[][] matrix = new Double[numDemes][numDemes];

        // construct matrix
        int index = 0;
        for (int i = 0; i < numDemes; i++) {
            for (int j = 0; j < numDemes; j++) {
                if (i != j) {
                    matrix[i][j] = rates[index];
                    index += 1;
                } else {
                    matrix[i][i] = popSizes[i];
                }
            }
        }
        return matrix;
    }

    public Value<Double[]> getTheta() {
        return getParams().get(thetaParamName);
    }

    public Value<Double[]> getMigrationRates() {
        return getParams().get(mParamName);
    }

}
