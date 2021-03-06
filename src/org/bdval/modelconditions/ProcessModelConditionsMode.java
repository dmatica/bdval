package org.bdval.modelconditions;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.UnflaggedOption;
import edu.cornell.med.icb.io.TsvToFromMap;
import edu.cornell.med.icb.iterators.TextFileLineIterator;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bdval.GenerateFinalModels;
import org.bdval.PredictedItems;
import org.bdval.modelselection.CandidateModelSelectionAllTeams;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * A mode to process model conditions in some way. Sub-classes of this mode implement useful behavior.
 *
 * @author Fabien Campagne
 *         Date: Oct 6, 2009
 *         Time: 4:00:42 PM
 */
public class ProcessModelConditionsMode extends edu.cornell.med.icb.cli.UseModality<ProcessModelConditionsOptions> {
    /**
     * Used to log debug and informational messages.
     */
    protected static final Log LOG = LogFactory.getLog(ProcessModelConditionsMode.class);
    private ProcessModelConditionsOptions options;


    /**
     * Define basic command line options for this mode.  Individual modes should override
     * making sure that options are reused or removed appropriately.  Options cannot
     * be defined more than once in {@link com.martiansoftware.jsap.JSAP}.
     *
     * @param jsap the JSAP command line parser
     * @throws com.martiansoftware.jsap.JSAPException
     *          if there is a problem building the options
     */
    @Override
    public void defineOptions(final JSAP jsap) throws JSAPException {
        super.defineOptions(jsap);    // help option is defined by the superclass call
        final UnflaggedOption resultsDirectoryOption = new UnflaggedOption("result-directory");

        resultsDirectoryOption.setStringParser(JSAP.STRING_PARSER)
                .setGreedy(true)
                .setHelp("Directory that contains results written by the bdval ant scripts.");

        jsap.registerParameter(resultsDirectoryOption);

        final Parameter modelconditionsOption = new FlaggedOption("model-conditions")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault(JSAP.NO_DEFAULT)
                .setRequired(true)
                .setShortFlag('c')
                .setLongFlag("model-conditions")
                .setHelp("The model condition file where models which are be processed are described.");
        jsap.registerParameter(modelconditionsOption);
    }

    @Override
    public void interpretArguments(final JSAP jsap, final JSAPResult jsapResult, final ProcessModelConditionsOptions options) {
        final String[] resultDirectories = jsapResult.getStringArray("result-directory");
        options.resultDirectories = resultDirectories;
        options.modelConditionsFilename = jsapResult.getString("model-conditions");
        options.modelConditionLines = GenerateFinalModels.readLines(options.modelConditionsFilename);
        options.modelConditions = readModelConditionsFile(options.modelConditionsFilename,
                new HashSet());

        CandidateModelSelectionAllTeams.addFeatureSelectionFoldColumn(options.modelConditions);
        CandidateModelSelectionAllTeams.addFeatureSelectionStatTypeColumn(options.modelConditions);
        CandidateModelSelectionAllTeams.addFeatureClassifierTypeColumn(options.modelConditions);

        setupRserve();
    }

    protected void setupRserve() {
        if (System.getProperty("RConnectionPool.configuration") == null) {
            System.setProperty("RConnectionPool.configuration", "config/RconnectionPool.xml");
            LOG.info("Will use the default location config/RconnectionPool.xml. Specify alternative location with -DRConnectionPool.configuration=<path>");
        }

    }

    @Override
    public void process(final ProcessModelConditionsOptions options) {
        this.options = options;

        for (final String modelId : options.modelConditions.keySet()) {
            processSeries(options, modelId);
        }
    }

    /**
     * Method to process a series
     *
     * @param options
     * @param modelId
     */
    public void processSeries(final ProcessModelConditionsOptions options, final String modelId) {
    }


    /**
     * This method is called to process one model id.
     * Details about the model id are available through the options:
     *
     * @param modelId
     */
    public void processOneModelIdPassOne(final ProcessModelConditionsOptions options, final String modelId) {
    }

    /**
     * This method is called to process one model id.
     * Details about the model id are available through the options:
     *
     * @param modelId
     */

    public void processOneModelIdPassTwo(final ProcessModelConditionsOptions options, final String modelId) {
    }


    /**
     * Read the model conditions file into a map of model id's to model conditions values.
     * This can handle both the normal version ("model-conditions.txt") or the columns version
     * ("model-conditions-columns.txt"). It can detect the difference as the first line of the
     * columns version ("model-conditions-columns.txt") starts with "model-id\t".
     *
     * @param modelConditionsFilename the model conditions file
     * @param keepModelIdsSet         the set of model id's to keep. If this set is null or empty
     *                                ALL model conditions will be read
     * @return the map of model id's to model conditions values
     */
    public static Map<String, Map<String, String>> readModelConditionsFile(
            final String modelConditionsFilename, final Set<String> keepModelIdsSet) {
        final File modelConditionsFile = new File(modelConditionsFilename);
        final Map<String, Map<String, String>> modelIdToModelConditionsMap =
                new Object2ObjectLinkedOpenHashMap<String, Map<String, String>>();
        if (!modelConditionsFile.exists()) {
            LOG.fatal("model conditions file " + modelConditionsFilename + " does not exist");
            return null;
        }
        if (!modelConditionsFile.isFile()) {
            LOG.fatal("model conditions file " + modelConditionsFilename + " is not a file");
            return null;
        }
        if (!modelConditionsFile.canRead()) {
            LOG.fatal("model conditions file " + modelConditionsFilename + " unreadable");
            return null;
        }

        try {
            int lineNo = 0;
            Boolean columnsMode = null;
            TsvToFromMap tsvToFromMap = null;
            for (final String line : new TextFileLineIterator(modelConditionsFile)) {
                if (columnsMode == null) {
                    columnsMode = line.startsWith("model-id\t");
                }

                final Map<String, String> modelValues;
                if (columnsMode) {
                    if (tsvToFromMap == null) {
                        tsvToFromMap = TsvToFromMap.createFromTsvFile(modelConditionsFile);
                    }
                    if (lineNo++ == 0 || StringUtils.isBlank(line) || line.startsWith("#")) {
                        continue;
                    }
                    modelValues = tsvToFromMap.readDataToMap(line);
                } else {
                    lineNo++;
                    if (StringUtils.isBlank(line) || line.startsWith("#")) {
                        continue;
                    }
                    modelValues = new Object2ObjectLinkedOpenHashMap<String, String>();
                    final String[] params = StringUtils.split(line, '\t');
                    for (final String param : params) {
                        final String[] parts = StringUtils.split(param, '=');
                        if (parts.length == 2) {
                            modelValues.put(parts[0], parts[1]);
                        } else {
                            // the value may contain a '=' for instance cache-dir=fs=true
                            final MutableString value = new MutableString();
                            for (int i = 1; i < parts.length; i++) {
                                value.append(parts[i]);
                            }
                            modelValues.put(parts[0], value.toString());

                        }
                    }
                }
                if (modelValues == null) {
                    continue;
                }
                final String modelId = modelValues.get("model-id");
                if (modelId == null || modelId.equals("N/A")) {
                    continue;
                }
                if (keepModelIdsSet == null
                        || keepModelIdsSet.size() == 0
                        || keepModelIdsSet.contains(modelId)) {

                    modelIdToModelConditionsMap.put(modelId, modelValues);
                }
            }
            return modelIdToModelConditionsMap;
        } catch (IOException e) {
            LOG.fatal("Error reading model conditions file " + modelConditionsFilename, e);
            return null;
        }
    }

    /**
     * Load the predictions corresponding to a model id.
     *
     * @param modelId
     * @return
     */
    public PredictedItems loadPredictions(final String modelId) {
        for (final String resultDirectory : options.resultDirectories) {
            String predictionsDirectoryPath = FilenameUtils.concat(resultDirectory, "predictions");

            predictionsDirectoryPath = FilenameUtils.concat(predictionsDirectoryPath, getDatasetName(modelId));
            final File predictionDir = new File(predictionsDirectoryPath);
            final String[] matchingFilenames = predictionDir.list(new FilenameFilter() {
                public boolean accept(final File file, final String name) {
                    return (name.contains(modelId));
                }
            });
            String predictionsFilename = "";
            if (matchingFilenames == null || matchingFilenames.length == 0) {
                LOG.trace(String.format("no prediction file was found for modelId=%s", modelId));
                continue;
            }
            if (matchingFilenames.length > 1) {
                System.err.println(String.format("more than one prediction file was found for modelId=%s", modelId));
                System.out.println("Filenames were : ");
                for (final String filename : matchingFilenames) {
                    System.out.println(filename);
                }
                System.out.flush();
                System.exit(1);
            } else {
                predictionsFilename = FilenameUtils.concat(predictionsDirectoryPath, matchingFilenames[0]);
            }
            try {
                final PredictedItems predictions = new PredictedItems();
                predictions.load(predictionsFilename);
                return predictions;
            } catch (IOException e) {
                LOG.fatal("An error occurred reading predictions file " + predictionsFilename, e);
                System.exit(1);
            }

        }
        return null;
    }

    protected String getDatasetName(final String modelId) {
        return options.modelConditions.get(modelId).get("dataset-name");
    }

    protected String constructLabel(final String modelId) {
        final String classifierType = options.modelConditions.get(modelId).get("classifier-type");
        final String featureSelectionType = options.modelConditions.get(modelId).get("feature-selection-type");
        final String featureSelectionMode = options.modelConditions.get(modelId).get("feature-selection-mode");
        String featureSelection = "unknown";
        if (featureSelectionMode != null) {
            featureSelection = featureSelectionMode;
        }
        if (featureSelectionType != null) {
            featureSelection = featureSelectionType;
        }
        final String classifierParameters = options.modelConditions.get(modelId).get("classifier-parameters");
        int removeThat = classifierParameters.lastIndexOf(",wekaClass");
        if (removeThat == -1) {
            removeThat = classifierParameters.length();
        }
        final String parameters = classifierParameters.substring(0, removeThat);
        return classifierType + '-' + featureSelection + '-' + parameters;
    }

}
