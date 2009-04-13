/*
 * Copyright (C) 2008-2009 Institute for Computational Biomedicine,
 *                         Weill Medical College of Cornell University
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.cornell.med.icb.biomarkers;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import edu.cornell.med.icb.learning.CrossValidation;
import edu.cornell.med.icb.tools.svmlight.EvaluationMeasure;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates statistics from a table of predictions generated by Predict.
 *
 * @author Fabien Campagne
 *         Date: Apr 6, 2008
 *         Time: 10:18:17 AM
 */
public class StatsMode extends Predict {
    /**
     * Used to log debug and error messages.
     */
    private static final Log LOG = LogFactory.getLog(StatsMode.class);
    private PredictedItems predictions;
    private String predictionsFilename;
    private final MaqciiHelper maqciiHelper = new MaqciiHelper();
    private String survivalFileName;

    @Override
    public void interpretArguments(final JSAP jsap, final JSAPResult result,
                                   final DAVOptions options) {
        predictionsFilename = result.getString("predictions");
        options.datasetName = result.getString("dataset-name");

        final String filename = new File(predictionsFilename).getName();
        if (options.datasetName.equals("auto")) {
            options.datasetName = filename.substring(0, filename.indexOf('-'));
        }
        try {
            predictions = new PredictedItems();
            predictions.load(predictionsFilename);
        } catch (IOException e) {
            LOG.fatal("An error occurred reading predictions file " + predictionsFilename, e);
            System.exit(1);
        }
        options.modelId = result.getString("model-id");
        if ("no_model_id".equals(options.modelId)) {
            // try to guess from predictions filename:
            // e.g., Cologne_OS_MO-genelists-NC01-2000-svmglobal-SIYCT-prediction-table.txt
            final Pattern pattern = Pattern.compile(".*(.....)-prediction-table.txt");

            final Matcher matcher = pattern.matcher(filename);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Can't extract model id from " + filename);
            }
            options.modelId = matcher.group(1);
        }

        if (result.contains("survival")) {
            survivalFileName = result.getString("survival");
        }

        if (survivalFileName == null || survivalFileName.equals("-")) {
            System.out.println("survival file not found!\t");
        } else {
            System.out.println("survival File" + survivalFileName);
        }

        if ("auto".equals(result.getString("label"))) {
            final String label = guessLabel(options.datasetName, filename);

            maqciiHelper.setupSubmissionFile(result, options, label);
        } else {
            maqciiHelper.setupSubmissionFile(result, options);
        }
    }

    public static String guessLabel(final String datasetName, final String filename) {
        return guessLabel(datasetName, filename, "-(.*)-(.....)-prediction-table.txt");
    }

    public static String guessLabel(final String datasetName, final String filename,
                                    final String patternTemplate) {
        // try to guess from predictions filename:
        // e.g., Cologne_OS_MO-genelists-NC01-2000-svmglobal-SIYCT-prediction-table.txt
        final Pattern pattern = Pattern.compile(datasetName + patternTemplate);
        final Matcher matcher = pattern.matcher(filename);
        final String label;
        if (matcher.matches()) {
            label = matcher.group(1);
        } else {
            label = null;
        }
        return label;
    }

    /**
     * Define command line options for this mode.
     * @param jsap the JSAP command line parser
     * @throws JSAPException if there is a problem building the options
     */
    @Override
    public void defineOptions(final JSAP jsap) throws JSAPException {
        final Parameter survivalFilenameOption = new FlaggedOption("survival")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault(JSAP.NO_DEFAULT)
                .setRequired(false)
                .setLongFlag("survival")
                .setHelp("Survival filename. This file contains survival data "
                        + "in tab delimited table; column 1: chipID has to match cids and "
                        + "tmm, column 2: time to event, column 3 censor with 1 as event 0 "
                        + "as censor, column 4 and beyond are all numerical covariates that "
                        + "will be included in the regression model");
        jsap.registerParameter(survivalFilenameOption);

        // there is no need for task definitions.
        jsap.getByID("task-list").addDefault("N/A");
        // there is no need for condition ids.
        jsap.getByID("conditions").addDefault("N/A");
        // there is no need for random seed.
        jsap.getByID("seed").addDefault("1");
        // there is no need for a gene list. The model has enough information to recreate it.
        jsap.getByID("gene-lists").addDefault("N/A");
        final Parameter inputFilenameOption = new FlaggedOption("predictions")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault(JSAP.NO_DEFAULT)
                .setRequired(true)
                .setLongFlag("predictions")
                .setHelp("Filename that contains the predictions "
                        + "in the format written by --mode predict.");
        jsap.registerParameter(inputFilenameOption);
        jsap.getByID("input").addDefault("N/A");
        jsap.getByID("platform-filenames").addDefault("N/A");

        maqciiHelper.defineSubmissionFileOption(jsap);
    }

    @Override
    public void process(final DAVOptions options) {
        final List<SurvivalMeasures> survivalMeasuresList = new ArrayList<SurvivalMeasures>();
        LOG.info("Calculating statistics for predictions in file " + predictionsFilename);
        final int numberOfRepeats = predictions.getNumberOfRepeats();
        final ObjectSet<CharSequence> evaluationMeasureNames = new ObjectArraySet<CharSequence>();
        evaluationMeasureNames.addAll(Arrays.asList(MEASURES));
        final EvaluationMeasure repeatedEvaluationMeasure = new EvaluationMeasure();
        for (int repeatId = 1; repeatId <= numberOfRepeats; repeatId++) {
            if (predictions.containsRepeat(repeatId)) {
                final DoubleList decisions = new DoubleArrayList();
                final DoubleList trueLabels = new DoubleArrayList();
                final ObjectList<String> sampleIDs = new ObjectArrayList<String>();

                decisions.addAll(predictions.getDecisionsForRepeat(repeatId));
                trueLabels.addAll(predictions.getTrueLabelsForRepeat(repeatId));
                sampleIDs.addAll(predictions.getSampleIDsForRepeat(repeatId));
                final EvaluationMeasure allSplitsInARepeatMeasure =
                        CrossValidation.testSetEvaluation(decisions.toDoubleArray(),
                                trueLabels.toDoubleArray(), evaluationMeasureNames, true);

                if (survivalFileName != null && !survivalFileName.equals("-")) {
                    try {
                        final SurvivalMeasures survivalMeasures = new SurvivalMeasures(
                                survivalFileName, decisions, trueLabels,
                                sampleIDs.toArray(new String [sampleIDs.size()]));
                        survivalMeasuresList.add(survivalMeasures);
                    } catch (IOException e) {
                        LOG.fatal("Error processing input file ", e);
                        System.exit(10);
                    }
                }

                for (final CharSequence measure : MEASURES) {
                    repeatedEvaluationMeasure.addValue(measure,
                            allSplitsInARepeatMeasure.getPerformanceValueAverage(measure.toString()));
                    final String binaryName = ("binary-" + measure).intern();
                    repeatedEvaluationMeasure.addValue(binaryName,
                            allSplitsInARepeatMeasure.getPerformanceValueAverage(binaryName));
                }
                LOG.info(String.format("repeatId: %d %s", repeatId, allSplitsInARepeatMeasure.toString()));
            }
        }

        final int numberOfFeatures = predictions.modelNumFeatures();
        if (survivalFileName != null || survivalFileName.equals("-")) {
            maqciiHelper.printSubmissionHeaders(options, true);
        } else {
            maqciiHelper.printSubmissionHeaders(options);
        }

        maqciiHelper.printSubmissionResults(options, repeatedEvaluationMeasure,
                numberOfFeatures, numberOfRepeats, survivalMeasuresList);
        LOG.info(String.format("Overall: %s", repeatedEvaluationMeasure.toString()));
    }
}
