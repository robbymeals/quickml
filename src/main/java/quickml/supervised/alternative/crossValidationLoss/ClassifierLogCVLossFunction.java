package quickml.supervised.alternative.crossValidationLoss;

public class ClassifierLogCVLossFunction implements ClassifierLossFunction {

    private static final double DEFAULT_MIN_PROBABILITY = 10E-7;
    public static final String NAME = "LOG_CV";
    public double minProbability;
    public double maxError;


    public ClassifierLogCVLossFunction(double minProbability) {
        this.minProbability = minProbability;
        this.maxError = -Math.log(minProbability);
    }

    private double lossForInstance(double correctProbability, double weight) {
        return (correctProbability > minProbability) ? -weight * Math.log(correctProbability) : weight * maxError;
    }

    @Override
    public double getLoss(PredictionMapResults results) {
        double totalLoss = 0;
        for (PredictionMapResult result : results) {
            totalLoss += lossForInstance(result.getPredictionForLabel(), result.getWeight());
        }
        return results.totalWeight() > 0 ? totalLoss / results.totalWeight() : 0;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
