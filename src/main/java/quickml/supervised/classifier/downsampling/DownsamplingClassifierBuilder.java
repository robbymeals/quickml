package quickml.supervised.classifier.downsampling;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickml.collections.MapUtils;
import quickml.supervised.PredictiveModelBuilder;
import quickml.supervised.alternative.optimizer.ClassifierInstance;
import quickml.supervised.classifier.Classifier;

import java.io.Serializable;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by ian on 4/22/14.
 */

public class DownsamplingClassifierBuilder implements PredictiveModelBuilder<DownsamplingClassifier, ClassifierInstance> {

    public static final String MINORITY_INSTANCE_PROPORTION = "minorityInstanceProportion";


    private static final Logger logger = LoggerFactory.getLogger(DownsamplingClassifierBuilder.class);
    private double targetMinorityProportion;
    private final PredictiveModelBuilder<Classifier, ClassifierInstance> predictiveModelBuilder;

    public DownsamplingClassifierBuilder(PredictiveModelBuilder<Classifier, ClassifierInstance> predictiveModelBuilder, double targetMinorityProportion) {
        checkArgument(targetMinorityProportion > 0 && targetMinorityProportion < 1, "targetMinorityProportion must be between 0 and 1 (was %s)", targetMinorityProportion);
        this.predictiveModelBuilder = predictiveModelBuilder;
        this.targetMinorityProportion = targetMinorityProportion;
    }

    @Override
    public void updateBuilderConfig(Map<String, Object> config) {
        predictiveModelBuilder.updateBuilderConfig(config);
        targetMinorityProportion((Double) config.get(MINORITY_INSTANCE_PROPORTION));
    }

    public DownsamplingClassifierBuilder targetMinorityProportion(double targetMinorityProportion) {
        this.targetMinorityProportion = targetMinorityProportion;
        return this;
    }

    @Override
    public DownsamplingClassifier buildPredictiveModel(final Iterable<ClassifierInstance> trainingData) {
        final Map<Serializable, Double> classificationProportions = getClassificationProportions(trainingData);
        if (classificationProportions.size() != 2) {
            printSampleInstancesForInspection(trainingData);
        }
        checkArgument(classificationProportions.size() == 2, "trainingData must contain only 2 classifications, but it had %s. mapOfClassificationsToOutcomes: %s", classificationProportions.size(), classificationProportions.get(1.0), classificationProportions.toString());
        final Map.Entry<Serializable, Double> majorityEntry = MapUtils.getEntryWithHighestValue(classificationProportions).get();
        final Map.Entry<Serializable, Double> minorityEntry = MapUtils.getEntryWithLowestValue(classificationProportions).get();
        Serializable majorityClassification = majorityEntry.getKey();
        final double majorityProportion = majorityEntry.getValue();
        final double naturalMinorityProportion = 1.0 - majorityProportion;
        if (naturalMinorityProportion >= targetMinorityProportion) {
            final Classifier wrappedPredictiveModel = predictiveModelBuilder.buildPredictiveModel(trainingData);
            return new DownsamplingClassifier(wrappedPredictiveModel, majorityClassification, minorityEntry.getKey(), 0);
        }

        final double dropProbability = (naturalMinorityProportion > targetMinorityProportion)?  0 : 1.0 - ((naturalMinorityProportion - targetMinorityProportion*naturalMinorityProportion) / (targetMinorityProportion - targetMinorityProportion *naturalMinorityProportion));

        Iterable<ClassifierInstance> downsampledTrainingData = Iterables.filter(trainingData, new RandomDroppingInstanceFilter(majorityClassification, dropProbability));

        final Classifier wrappedPredictiveModel = predictiveModelBuilder.buildPredictiveModel(downsampledTrainingData);

        return new DownsamplingClassifier(wrappedPredictiveModel, majorityClassification, minorityEntry.getKey(), dropProbability);
    }

    private void printSampleInstancesForInspection(Iterable<ClassifierInstance> trainingData) {
        logger.info("length of training data" + Iterables.size(trainingData));
        int counter = 0;
        for (ClassifierInstance instance : trainingData) {
            if (counter++ % 100 == 0) {
                if (instance.getLabel().equals(Double.valueOf(1.0))) {
                    logger.info("instance " + counter);
                    logger.info(instance.getAttributes().toString());
                    logger.info("label:" + instance.getLabel().toString());
                    logger.info("weight:" + instance.getWeight());
                }
            }
            if (counter > 1000) break;
        }
    }

    private Map<Serializable, Double> getClassificationProportions(final Iterable<ClassifierInstance> trainingData) {
        Map<Serializable, Long> classificationCounts = Maps.newHashMap();
        long total = 0;
        for (ClassifierInstance instance : trainingData) {
            Long count = classificationCounts.get(instance.getLabel());
            if (count == null) {
                count = 0L;
                classificationCounts.put(instance.getLabel(), count);
            }
            count++;
            total++;
        }
        Map<Serializable, Double> classificationProportions = Maps.newHashMap();
        for (Map.Entry<Serializable, Long> classCount : classificationCounts.entrySet()) {
            classificationProportions.put(classCount.getKey(),  classCount.getValue().doubleValue() / (double) total);
        }
        return classificationProportions;
    }
}