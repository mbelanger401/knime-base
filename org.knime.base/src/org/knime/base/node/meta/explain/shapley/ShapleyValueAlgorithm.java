/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
 * ---------------------------------------------------------------------
 *
 * History
 *   06.03.2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shapley;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.knime.base.node.meta.explain.shapley.FeatureReplacer.ReplacementResult;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.util.CheckUtils;

/**
 * Estimates the Shapley Values using algorithm 1 proposed by Strumbelj and Kononenko in their paper "Explaining
 * prediction models and individual predictions with feature contributions"
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class ShapleyValueAlgorithm {

    /**
    *
    */
    private static final String DELIMITER = "_";

    private static final String FOI_REPLACED = "t";

    private static final String FOI_NOT_REPLACED = "f";

    private final FeatureReplacer m_featureReplacer;

    private final int m_iterationsPerFeature;

    private final int m_numTargetCols;

    /**
     * Estimates the Shapley Values using algorithm 1 proposed by Strumbelj and Kononenko in their paper "Explaining
     * prediction models and individual predictions with feature contributions"
     *
     * @param featureReplacer allows to transform a single row to obtain multiple transformed rows
     * @param iterationsPerFeature the number of iterations to perform per feature
     * @param numTargetCols the number of target columns
     */
    public ShapleyValueAlgorithm(final FeatureReplacer featureReplacer, final int iterationsPerFeature,
        final int numTargetCols) {
        CheckUtils.checkArgument(numTargetCols > 0, "At least one prediction column must be included.");
        CheckUtils.checkArgument(iterationsPerFeature > 0,
            "The number of iterations per feature must be larger than 0.");
        CheckUtils.checkNotNull(featureReplacer);
        m_featureReplacer = featureReplacer;
        m_iterationsPerFeature = iterationsPerFeature;
        m_numTargetCols = numTargetCols;
    }

    List<DataRow> prepareRow(final DataRow row) {
        final List<DataRow> rows = new ArrayList<>(2 * row.getNumCells() * m_iterationsPerFeature);
        final RowKeyGenerator keyGen = new RowKeyGenerator(row.getKey());
        for (int i = 0; i < m_featureReplacer.getFeatureCount(); i++) {
            for (int j = 0; j < m_iterationsPerFeature; j++) {
                final ReplacementResult r = m_featureReplacer.replaceFeatures(row, i);
                rows.add(new DefaultRow(keyGen.createKey(i, j, false), r.getFoiIntact()));
                rows.add(new DefaultRow(keyGen.createKey(i, j, true), r.getFoiReplaced()));
            }
        }
        return rows;
    }

    DataRow calculateShapleyValuesForNextRow(final Iterator<DataRow> rows) {
        final PeekingIterator<DataRow> iterator = new PeekingIterator<>(rows);
        final RowKey firstKey = iterator.peek().getKey();
        final RowKeyChecker checker = new RowKeyChecker(firstKey);
        final double[] shapleyValues = calculateShapleyValues(iterator, checker);
        return new DefaultRow(new RowKey(checker.getOriginalKey()), shapleyValues);
    }

    /**
     * @param iterator provides the rows for the current row batch
     * @param checker used to ensure that all rows in the current row batch belong together and are in the right order
     * @return a double array containing the Shapley Values
     */
    private double[] calculateShapleyValues(final Iterator<DataRow> iterator, final RowKeyChecker checker) {
        final int featureCount = m_featureReplacer.getFeatureCount();
        final double[] shapleyValues = new double[featureCount];
        for (int i = 0; i < featureCount; i++) {
            double currentShapleyValue = 0.0;
            for (int j = 0; j < m_iterationsPerFeature; j++) {
                final DataRow foiIntact = iterator.next();
                final DataRow foiReplaced = iterator.next();
                checker.checkRowKey(foiIntact.getKey(), i, j, false);
                checker.checkRowKey(foiReplaced.getKey(), i, j, true);
                currentShapleyValue += getDifferenceInPredictions(foiIntact, foiReplaced);
                if (!iterator.hasNext()) {
                    // Check if we expect to be at the end
                    CheckUtils.checkState(i == featureCount - 1, "Not all transformed rows arrived in the loop end.");
                    CheckUtils.checkState(j == m_iterationsPerFeature - 1,
                        "Not all transformed rows arrived in the loop end.");
                }
            }
            shapleyValues[i] = currentShapleyValue / m_iterationsPerFeature;
        }
        return shapleyValues;
    }

    private static double getDifferenceInPredictions(final DataRow foiReplaced, final DataRow foiIntact) {
        // TODO generalize to cases with multiple predictions
        return getPrediction(foiIntact) - getPrediction(foiReplaced);
    }

    private static double getPrediction(final DataRow row) {
        // TODO generalize to cases with multiple predictions
        final DataCell targetCell = row.getCell(row.getNumCells() - 1);
        if (targetCell instanceof DoubleValue) {
            final DoubleValue val = (DoubleValue)targetCell;
            return val.getDoubleValue();
        } else {
            throw new IllegalArgumentException("The prediction column of '" + row + "' is not numerical.");
        }
    }

    private static class RowKeyChecker {
        /**
         * The number of components that the row key generator adds to the original row key
         */
        private static final int NUM_ADDITIONAL_COMPONENTS = 3;

        /**
         * Distance of the iteration component from the end of a generated row key
         */
        private static final int POS_ITERATION = 2;

        /**
         * Minimal number of components of a generated row key
         */
        private static final int MIN_COMPONENTS_PER_KEY = 4;

        /**
         * Distance of the feature index component from the end of the split row key
         */
        private static final int POS_FEATURE_IDX = 3;

        private final String m_originalKey;

        /**
         * Checks if a row belongs to the current batch. </br>
         * Has to be initialized with the {@link RowKey} of the first row in the batch. Fails if the first row key does
         * not designate the start of a row batch.
         *
         * @param keyOfFirstRowInBatch the row key of the first row in the current row batch
         */
        public RowKeyChecker(final RowKey keyOfFirstRowInBatch) {
            final RowKey key = keyOfFirstRowInBatch;
            final String[] split = split(key);
            int featureIdx = -1;
            int iteration = -1;
            try {
                featureIdx = parseFeatureIdx(split);
                iteration = parseIteration(split);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(createErrorString(key));
            }
            CheckUtils.checkArgument(featureIdx == 0 && iteration == 0,
                "The row with key '" + key + "' is not the first row in a row batch.");
            m_originalKey = recreateOriginalKey(split);
        }

        public String getOriginalKey() {
            return m_originalKey;
        }

        private static String createErrorString(final RowKey generatedKey) {
            return "The row key '" + generatedKey + "' was not created by an instance of this RowKeyGenerator.";
        }

        /**
         * @param split
         * @return
         */
        private static int parseIteration(final String[] split) {
            return Integer.parseInt(split[split.length - POS_ITERATION]);
        }

        private static int parseFeatureIdx(final String[] split) {
            return Integer.parseInt(split[split.length - POS_FEATURE_IDX]);
        }

        private static String[] split(final RowKey key) {
            final String[] split = key.getString().split(DELIMITER);
            CheckUtils.checkArgument(split.length >= MIN_COMPONENTS_PER_KEY, createErrorString(key));
            return split;
        }

        private static String recreateOriginalKey(final String[] split) {
            // TODO figure out if there is a more efficient way to do this
            return Arrays.stream(split, 0, split.length - NUM_ADDITIONAL_COMPONENTS)
                .collect(Collectors.joining(DELIMITER));
        }

        private static boolean isFoiReplaced(final String[] split) {
            return split[split.length - 1].equals(FOI_REPLACED);
        }

        /**
         * Checks whether the provided <b>key</b> belongs to the row that is currently expected.
         *
         * @param key to check
         * @param expectedFeatureIdx the feature index the current row should have
         * @param expectedIteration iteration the current row should have
         * @param withFoiReplaced whether the FOI should be replaced in the current row
         */
        public void checkRowKey(final RowKey key, final int expectedFeatureIdx, final int expectedIteration,
            final boolean withFoiReplaced) {
            final String[] split = split(key);
            final String originalKey = recreateOriginalKey(split);
            CheckUtils.checkArgument(m_originalKey.equals(originalKey),
                "The row with key '" + key + "' does not belong to the current batch of rows.");
            CheckUtils.checkArgument(isRightOrder(expectedFeatureIdx, expectedIteration, withFoiReplaced, split),
                "The rows corresponding to the original row key '" + m_originalKey
                    + "' are not in the expected order.");
        }

        private static boolean isRightOrder(final int expectedFeatureIdx, final int expectedIteration,
            final boolean expectFoiReplaced, final String[] split) {
            return expectedFeatureIdx == parseFeatureIdx(split) && expectedIteration == parseIteration(split)
                && isFoiReplaced(split) == expectFoiReplaced;
        }
    }

    private static class RowKeyGenerator {

        private String m_originalKey;

        private RowKeyGenerator(final RowKey originalKey) {
            m_originalKey = originalKey.getString();
        }

        RowKey createKey(final int featureIdx, final int iteration, final boolean withFoiReplaced) {
            StringBuilder sb = new StringBuilder(m_originalKey);
            sb.append(DELIMITER);
            sb.append(featureIdx);
            sb.append(DELIMITER);
            sb.append(iteration);
            sb.append(DELIMITER);
            sb.append(withFoiReplaced ? FOI_REPLACED : FOI_NOT_REPLACED);
            return new RowKey(sb.toString());
        }

    }

}
