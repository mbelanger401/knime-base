/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 */

package org.knime.base.node.mine.bayes.naivebayes.datamodel;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.BayesInputDocument.BayesInput;
import org.dmg.pmml.GaussianDistributionDocument.GaussianDistribution;
import org.dmg.pmml.TargetValueStatDocument.TargetValueStat;
import org.dmg.pmml.TargetValueStatsDocument.TargetValueStats;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataValue;
import org.knime.core.data.DoubleValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.Config;
import org.knime.core.util.MutableInteger;



/**
 *This {@link AttributeModel} implementation calculates the probability for
 *numerical attributes by assuming a Gaussian distribution of the data.
 * @author Tobias Koetter, University of Konstanz
 */
class NumericalAttributeModel extends AttributeModel {

    /**
     * The unique type of this model used for saving/loading.
     */
    static final String MODEL_TYPE = "NumericalModel";

    private static final String CLASS_VALUE_COUNTER = "noOfClasses";

    private static final String CLASS_VALUE_SECTION = "classValueData_";

//    private static final int HTML_VIEW_SIZE = 5;

    private final class NumericalClassValue {

        private static final String CLASS_VALUE = "classValue";

        private static final String MISSING_VALUE_COUNTER = "MissingValCounter";

        private static final String NO_OF_ROWS = "noOfRows";

        private static final String SUM = "sum";

        private static final String SQUARE_SUM = "squareSum";

        private final String m_classValue;

        private int m_noOfRows = 0;

        private double m_sum = 0;

        private double m_squareSum = 0;

        private double m_mean;

        private double m_stdDeviation = 0;

        private double m_probabilityDenominator = 0;

        private final MutableInteger m_missingValueRecs = new MutableInteger(0);

        private boolean m_recompute = true;

        /**Constructor for class NumericalRowValue.NumericalClassValue.
         * @param classValue the value of this class
         *
         */
        NumericalClassValue(final String classValue) {
            m_classValue = classValue;
        }

        /**Constructor for class NumericalClassValue.
         * @param config the <code>Config</code> object to read from
         * @throws InvalidSettingsException if the settings are invalid
         */
        NumericalClassValue(final Config config)
            throws InvalidSettingsException {
            m_classValue = config.getString(CLASS_VALUE);
            m_missingValueRecs.setValue(config.getInt(MISSING_VALUE_COUNTER));
            m_noOfRows = config.getInt(NO_OF_ROWS);
            m_sum = config.getDouble(SUM);
            m_squareSum = config.getDouble(SQUARE_SUM);
        }

        /**Constructor for class NumericalClassValue.
         * @param targetValueStat the <code>TargetValueStat</code> object to read from
         * @throws InvalidSettingsException if the settings are invalid
         */
        NumericalClassValue(final TargetValueStat targetValueStat) throws InvalidSettingsException {
            m_classValue = targetValueStat.getValue();
            final GaussianDistribution distribution = targetValueStat.getGaussianDistribution();
            m_mean = distribution.getMean();
            final Map<String, String> extensionMap =
                    PMMLNaiveBayesModelTranslator.convertToMap(targetValueStat.getExtensionList());
            if (extensionMap.containsKey(MISSING_VALUE_COUNTER)) {
                m_missingValueRecs.setValue(
                    PMMLNaiveBayesModelTranslator.getIntExtension(extensionMap, MISSING_VALUE_COUNTER));
                m_noOfRows = PMMLNaiveBayesModelTranslator.getIntExtension(extensionMap, NO_OF_ROWS);
            }
            if (extensionMap.containsKey(SUM)) {
                m_sum = PMMLNaiveBayesModelTranslator.getDoubleExtension(extensionMap, SUM);
                m_squareSum = PMMLNaiveBayesModelTranslator.getDoubleExtension(extensionMap, SQUARE_SUM);
                calculateProbabilityValues();
            } else {
                final double variance = distribution.getVariance();
                m_stdDeviation = Math.sqrt(variance);
                if (m_stdDeviation == 0) {
                    m_probabilityDenominator = 0;
                } else {
                    m_probabilityDenominator = 2 * variance;
                }
            }
            m_recompute = false;
        }

        /**
         * @param config the <code>Config</code> object to write to
         */
        void saveModel(final Config config) {
            config.addString(CLASS_VALUE, m_classValue);
            config.addInt(MISSING_VALUE_COUNTER, m_missingValueRecs.intValue());
            config.addInt(NO_OF_ROWS, m_noOfRows);
            config.addDouble(SUM, m_sum);
            config.addDouble(SQUARE_SUM, m_squareSum);
        }

        /**
         * @param targetValueStats
         */
        void exportToPMML(final TargetValueStat targetValueStat) {
            targetValueStat.setValue(getClassValue());
            if (!ignoreMissingVals()) {
                PMMLNaiveBayesModelTranslator.setIntExtension(targetValueStat.addNewExtension(), MISSING_VALUE_COUNTER,
                    m_missingValueRecs.intValue());
                PMMLNaiveBayesModelTranslator.setIntExtension(targetValueStat.addNewExtension(), NO_OF_ROWS,
                    getNoOfRows());
            }
//            PMMLNaiveBayesModelTranslator.setDoubleExtension(targetValueStat.addNewExtension(), SUM, m_sum);
//            PMMLNaiveBayesModelTranslator.setDoubleExtension(targetValueStat.addNewExtension(), SQUARE_SUM,
//                m_squareSum);
            final GaussianDistribution distribution = targetValueStat.addNewGaussianDistribution();
            distribution.setMean(getMean());
            distribution.setVariance(getVariance());
        }

        /**
         * @return the classValue
         */
        String getClassValue() {
            return m_classValue;
        }


        /**
         * @return the noOfRows
         */
        int getNoOfRows() {
            return m_noOfRows;
        }

        /**
         * @return the mean
         */
        double getMean() {
            if (m_recompute) {
                calculateProbabilityValues();
            }
            return m_mean;
        }


        /**
         * @return the standard deviation
         */
        double getStdDeviation() {
            if (m_recompute) {
                calculateProbabilityValues();
            }
            return m_stdDeviation;
        }

        /**
         * @return the variance
         */
        double getVariance() {
            return Math.pow(getStdDeviation(), 2);
        }

        /**
         * @param attrVal the attribute value to add to this class
         */
        void addValue(final DataCell attrVal) {
            if (attrVal.isMissing()) {
                m_missingValueRecs.inc();
            } else {
                final double doubleValue =
                    ((DoubleValue)attrVal).getDoubleValue();
                m_sum += doubleValue;
                m_squareSum += (doubleValue * doubleValue);
                m_recompute = true;
            }
            m_noOfRows++;
        }

        /**
         * @param attrVal the attribute value to calculate the probability for
         * @return the calculated probability for the given attribute
         */
        double getProbability(final DataCell attrVal) {
            if (m_recompute) {
                calculateProbabilityValues();
            }
            if (attrVal.isMissing()) {
                if (m_noOfRows == 0) {
                    return 0;
                }
                // TODO Can we add laplace correction here?
                return (double)m_missingValueRecs.intValue() / m_noOfRows;
            }
            final double attrValue = ((DoubleValue)attrVal).getDoubleValue();
            final double diff = attrValue - m_mean;
            if (m_stdDeviation == 0) {
                //if the standard deviation is 0 which means that
                //the probability is 1 if this attribute value
                //is equal the mean (which is equal to the only observed)
                //otherwise it is 0
                if (diff == 0) {
                    return 1;
                }
                return 0;
            }
            if (m_probabilityDenominator == 0) {
                //this should never happen since we check the standard deviation
                throw new IllegalStateException("Error while calculating "
                        + "probability for attribute " + getAttributeName()
                        + ": Probability denominator was zero");
            }
            //we do not use the probability factor
            //1 / (PROB_FACT_DEN * m_stdDeviation) which ensures that the area
            //under the distribution function is 1 to have a probability result
            //between 1 and 0. If we use the probability factor for columns
            //with a very low variance the probability is > 1 which might result
            //in a number overflow for many of such columns like described
            //in forum post http://www.knime.org/node/949
            final double prob =
                Math.exp(-(diff * diff
                        / m_probabilityDenominator));
            return prob;
        }


        /**
         * Called after all training rows where added to validate the model.
         * @throws InvalidSettingsException if the model isn't valid
         */
        void validate() throws InvalidSettingsException {
            if (m_noOfRows == 0) {
                setInvalidCause(MODEL_CONTAINS_NO_RECORDS);
                throw new InvalidSettingsException("Model for attribute "
                        + getAttributeName() + " contains no rows.");
            }
            calculateProbabilityValues();
        }

        private void calculateProbabilityValues() {
            if (m_noOfRows == 0) {
                throw new IllegalStateException("Model for attribute "
                        + getAttributeName() + " contains no rows.");
            }
            final int noOfRowsNonMissing =
                m_noOfRows - m_missingValueRecs.intValue();
            // TODO Verify this! What if training data only contains missing values
            if (noOfRowsNonMissing == 0) {
                throw new IllegalStateException("Model for attribute "
                        + getAttributeName() + " and class \""
                        + getClassValue() + "\" contains only missing values");
            }
            m_mean = m_sum / noOfRowsNonMissing;
            if (noOfRowsNonMissing == 1) {
                m_stdDeviation = 0;
            } else {
                final double intermediateResult = (m_squareSum
                        - ((m_sum * m_sum) / noOfRowsNonMissing))
                        / (noOfRowsNonMissing - 1);
                if (intermediateResult <= 0) {
                    //this should never happen but could be a rounding problem
                    m_stdDeviation = 0;
                } else {
                    m_stdDeviation = Math.sqrt(intermediateResult);
                }
            }
            if (m_stdDeviation == 0) {
                m_probabilityDenominator = 0;
            } else {
                m_probabilityDenominator = 2 * m_stdDeviation * m_stdDeviation;
            }
            m_recompute = false;
        }


        /**
         * @return the missingValueRecs
         */
        public MutableInteger getMissingValueRecs() {
            return m_missingValueRecs;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            if (m_recompute) {
                calculateProbabilityValues();
            }
            final StringBuilder buf = new StringBuilder();
            buf.append(m_classValue);
            buf.append("\n");
            buf.append("Standard deviation: ");
            buf.append(m_stdDeviation);
            buf.append("\n");
            buf.append("Mean: ");
            buf.append(m_mean);
            buf.append("\n");
            buf.append("No of rows: ");
            buf.append(m_noOfRows);
            buf.append("\n");
            buf.append("Sum: ");
            buf.append(m_sum);
            buf.append("\n");
            buf.append("SquareSum: ");
            buf.append(m_squareSum);
            buf.append("\n");
            buf.append("Probability denominator: ");
            buf.append(m_probabilityDenominator);
            buf.append("\n");
            buf.append("Missing values: ");
            buf.append(m_missingValueRecs);
            buf.append("\n");
            return buf.toString();
        }
    }

    private final Map<String, NumericalClassValue> m_classValues;

    /**Constructor for class NumericalRowValue.
     * @param attributeName the row caption
     * @param skipMissingVals set to <code>true</code> if the missing values
     * should be skipped during learning and prediction
     */
    NumericalAttributeModel(final String attributeName,
            final boolean skipMissingVals) {
        super(attributeName, 0, skipMissingVals);
        m_classValues = new HashMap<>();
    }

    /**Constructor for class NumericalAttributeModel.
     * @param attributeName the name of the attribute
     * @param skipMissingVals set to <code>true</code> if the missing values
     * should be skipped during learning and prediction
     * @param noOfMissingVals the number of missing values
     * @param config the <code>Config</code> object to read from
     * @throws InvalidSettingsException if the settings are invalid
     */
    NumericalAttributeModel(final String attributeName,
            final boolean skipMissingVals, final int noOfMissingVals,
            final Config config)
    throws InvalidSettingsException {
        super(attributeName, noOfMissingVals, skipMissingVals);
        final int noOfClasses = config.getInt(CLASS_VALUE_COUNTER);
        m_classValues = new HashMap<>(noOfClasses);
        for (int i = 0; i < noOfClasses; i++) {
            final Config classConfig =
                config.getConfig(CLASS_VALUE_SECTION + i);
            final NumericalClassValue classVal =
                new NumericalClassValue(classConfig);
            m_classValues.put(classVal.getClassValue(), classVal);
        }
    }

    /**Constructor for class NumericalAttributeModel.
     * @param attributeName the name of the attribute
     * @param ignoreMissingVals set to <code>true</code> if the missing values
     * should be skipped during learning and prediction
     * @param noOfMissingVals the number of missing values
     * @param bayesInput the <code>BayesInput</code> object to read from
     * @throws InvalidSettingsException if the settings are invalid
     */
    NumericalAttributeModel(final String attributeName, final boolean ignoreMissingVals,
        final int noOfMissingVals, final BayesInput bayesInput)  throws InvalidSettingsException {
        super(attributeName, noOfMissingVals, ignoreMissingVals);
        TargetValueStats targetValueStats = bayesInput.getTargetValueStats();
        List<TargetValueStat> targetValueStatList = targetValueStats.getTargetValueStatList();
        m_classValues = new HashMap<>(targetValueStatList.size());
        for (TargetValueStat targetValueStat : targetValueStatList) {
            NumericalClassValue classValue = new NumericalClassValue(targetValueStat);
            m_classValues.put(classValue.getClassValue(), classValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void saveModelInternal(final Config config) {
        config.addInt(CLASS_VALUE_COUNTER, m_classValues.size());
        int i = 0;
        for (final NumericalClassValue classVal : m_classValues.values()) {
            final Config classConfig =
                config.addConfig(CLASS_VALUE_SECTION + i);
            classVal.saveModel(classConfig);
            i++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void exportToPMMLInternal(final BayesInput bayesInput) {
        final TargetValueStats targetValueStats = bayesInput.addNewTargetValueStats();
        for (final NumericalClassValue classVal : m_classValues.values()) {
            final TargetValueStat targetValueStat = targetValueStats.addNewTargetValueStat();
            classVal.exportToPMML(targetValueStat);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void addValueInternal(final String classValue,
            final DataCell attrValue) {
        NumericalClassValue classObject = m_classValues.get(classValue);
        if (classObject == null) {
            classObject = new NumericalClassValue(classValue);
            m_classValues.put(classValue, classObject);
        }
        classObject.addValue(attrValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void validate() throws InvalidSettingsException {
        if (m_classValues.size() == 0) {
            setInvalidCause(MODEL_CONTAINS_NO_CLASS_VALUES);
            throw new InvalidSettingsException("Model for attribute "
                    + getAttributeName() + " contains no class values");
        }
        final Collection<NumericalClassValue> classVals =
            m_classValues.values();
        for (final NumericalClassValue value : classVals) {
            value.validate();
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    Class<? extends DataValue> getCompatibleType() {
        return DoubleValue.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Collection<String> getClassValues() {
        return m_classValues.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Integer getNoOfRecs4ClassValue(final String classValue) {
        final NumericalClassValue value = m_classValues.get(classValue);
        if (value == null) {
            return null;
        }
        return new Integer(value.getNoOfRows());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    double getProbabilityInternal(final String classValue,
            final DataCell attributeValue, final double laplaceCorrector,
            final boolean useLog) {
        final NumericalClassValue classModel = m_classValues.get(classValue);
        if (classModel == null) {
            return 0;
        }
        return classModel.getProbability(attributeValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getHTMLViewHeadLine() {
        return "Gaussian distribution for " + getAttributeName()
        + " per class value";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getType() {
        return MODEL_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getHTMLView(final int totalNoOfRecs) {
        final List<String> sortedClassVal =
            AttributeModel.sortCollection(m_classValues.keySet());
        if (sortedClassVal == null) {
            return "";
        }
        final String missingHeader = getMissingValueHeader(getClassValues());
        //create the value rows
        final StringBuilder countRow = new StringBuilder();
        final StringBuilder meanRow = new StringBuilder();
        final StringBuilder stdDevRow = new StringBuilder();
        final StringBuilder rateRow = new StringBuilder();
        final StringBuilder missingRow = new StringBuilder();
        for (final String classVal : sortedClassVal) {
            final NumericalClassValue classValue = m_classValues.get(classVal);
            countRow.append("<td align='center'>");
            countRow.append(classValue.getNoOfRows());
            countRow.append("</td>");

            meanRow.append("<td align='center'>");
            meanRow.append(NaiveBayesModel.HTML_VALUE_FORMATER.format(
                    classValue.getMean()));
            meanRow.append("</td>");

            stdDevRow.append("<td align='center'>");
            stdDevRow.append(NaiveBayesModel.HTML_VALUE_FORMATER.format(
                    classValue.getStdDeviation()));
            stdDevRow.append("</td>");

            rateRow.append("<td align='center'>");
            rateRow.append(classValue.getNoOfRows());
            rateRow.append('/');
            rateRow.append(totalNoOfRecs);
            rateRow.append("</td>");

            if (missingHeader != null) {
                missingRow.append("<td align='center'>");
                missingRow.append(classValue.getMissingValueRecs());
                missingRow.append("</td>");
            }
        }

        final StringBuilder buf = new StringBuilder();
        buf.append("<table border='1' width='100%'>");
        buf.append(createTableHeader(" ", sortedClassVal, null));
        //append the count row
        buf.append("<tr>");
        buf.append("<th>");
        buf.append("Count:");
        buf.append("</th>");
        buf.append(countRow);
        buf.append("</tr>");
        //append the mean row
        buf.append("<tr>");
        buf.append("<th>");
        buf.append("Mean:");
        buf.append("</th>");
        buf.append(meanRow);
        buf.append("</tr>");
        //append the Std. Deviation row
        buf.append("<tr>");
        buf.append("<th>");
        buf.append("Std. Deviation:");
        buf.append("</th>");
        buf.append(stdDevRow);
        buf.append("</tr>");

        //append the missing val row
        if (missingHeader != null) {
              buf.append("<tr>");
              buf.append("<th>");
              buf.append("Missing values:");
              buf.append("</th>");
              buf.append(missingRow);
              buf.append("</tr>");
          }

        //append the rate row
        buf.append("<tr>");
        buf.append("<th>");
        buf.append("Rate:");
        buf.append("</th>");
        buf.append(rateRow);
        buf.append("</tr>");

        buf.append("</table>");
        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(getAttributeName());
        buf.append("\n");
        buf.append(getType());
        buf.append("\n");
        for (final NumericalClassValue classVal : m_classValues.values()) {
            buf.append(classVal.toString());
            buf.append("\n");
        }
        return buf.toString();
    }
}
