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
import java.util.stream.IntStream;

import org.knime.base.node.meta.explain.shapley.FeatureReplacer.ReplacementResult;
import org.knime.base.node.meta.explain.shapley.KeyGeneratorFactory.RowKeyChecker;
import org.knime.base.node.meta.explain.shapley.KeyGeneratorFactory.RowKeyGenerator;
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
public class ShapleyValuesAlgorithm {

    private final FeatureReplacer m_featureReplacer;

    private final int m_iterationsPerFeature;

    private final int m_numTargetCols;

    private static final KeyGeneratorFactory<RowKey, SVId> KEY_GEN_FAC =
        new ShapleyValuesKeyGeneratorFactory<>(RowKey::new, RowKey::getString);

    /**
     * Estimates the Shapley Values using algorithm 1 proposed by Strumbelj and Kononenko in their paper "Explaining
     * prediction models and individual predictions with feature contributions"
     *
     * @param featureReplacer allows to transform a single row to obtain multiple transformed rows
     * @param iterationsPerFeature the number of iterations to perform per feature
     * @param numTargetCols the number of target columns
     */
    public ShapleyValuesAlgorithm(final FeatureReplacer featureReplacer, final int iterationsPerFeature,
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
        final List<DataRow> rows = new ArrayList<>(2 * m_featureReplacer.getFeatureCount() * m_iterationsPerFeature);
        final RowKeyGenerator<RowKey, SVId> keyGen = KEY_GEN_FAC.createGenerator(row.getKey());
        final SVId svId = new SVId(0, 0, false);
        for (int i = 0; i < m_featureReplacer.getFeatureCount(); i++) {
            svId.setFeatureIdx(i);
            for (int j = 0; j < m_iterationsPerFeature; j++) {
                final ReplacementResult r = m_featureReplacer.replaceFeatures(row, i);
                svId.setIteration(j);
                svId.setFoiIntact(true);
                rows.add(new DefaultRow(keyGen.create(svId), r.getFoiIntact()));
                svId.setFoiIntact(false);
                rows.add(new DefaultRow(keyGen.create(svId), r.getFoiReplaced()));
            }
        }
        return rows;
    }

    DataRow calculateShapleyValuesForNextRow(final Iterator<DataRow> rows) {
        final PeekingIterator<DataRow> iterator = new PeekingIterator<>(rows);
        final RowKey firstKey = iterator.peek().getKey();
        final RowKeyChecker<RowKey, SVId> checker = KEY_GEN_FAC.createChecker(firstKey);
        final ShapleyValues shapleyValues = calculateShapleyValues(iterator, checker);
        return new DefaultRow(new RowKey(checker.getOriginalKey()), shapleyValues.getShapleyValues());
    }

    private ShapleyValues calculateShapleyValues(final Iterator<DataRow> iterator,
        final RowKeyChecker<RowKey, SVId> checker) {
        final int[] allFeatures = IntStream.range(0, m_featureReplacer.getFeatureCount()).toArray();
        return calculateShapleyValues(iterator, checker, allFeatures);
    }

    /**
     * @param iterator provides the rows for the current row batch
     * @param checker used to ensure that all rows in the current row batch belong together and are in the right order
     * @return a double array containing the Shapley Values
     */
    private ShapleyValues calculateShapleyValues(final Iterator<DataRow> iterator,
        final RowKeyChecker<RowKey, SVId> checker, final int[] features) {
        final int featureCount = m_featureReplacer.getFeatureCount();
        final ShapleyValues shapleyValues = new ShapleyValues(featureCount, m_numTargetCols, m_iterationsPerFeature);
        final DiffAccumulator diffAccumulator = new DiffAccumulator(m_numTargetCols);
        final SVId svId = new SVId();
        for (int i : features) {
            diffAccumulator.reset();
            svId.setFeatureIdx(i);
            for (int j = 0; j < m_iterationsPerFeature; j++) {
                final DataRow foiIntact = iterator.next();
                final DataRow foiReplaced = iterator.next();
                svId.setIteration(j);
                svId.setFoiIntact(true);
                checker.check(foiIntact.getKey(), svId);
                svId.setFoiIntact(false);
                checker.check(foiReplaced.getKey(), svId);
                diffAccumulator.update(foiIntact, foiReplaced);
                if (!iterator.hasNext()) {
                    // Check if we expect to be at the end
                    CheckUtils.checkState(i == featureCount - 1, "Not all transformed rows arrived in the loop end.");
                    CheckUtils.checkState(j == m_iterationsPerFeature - 1,
                        "Not all transformed rows arrived in the loop end.");
                }
            }
            shapleyValues.updateValues(i, diffAccumulator);
        }
        return shapleyValues;
    }

    private static class ShapleyValues {
        private final double[] m_data;

        private final int m_iterationsPerFeature;

        private final int m_numTargets;

        ShapleyValues(final int numFeatures, final int numTargets, final int iterationsPerFeature) {
            m_numTargets = numTargets;
            m_iterationsPerFeature = iterationsPerFeature;
            m_data = new double[numTargets * numFeatures];
        }

        private void set(final double value, final int featureIdx, final int targetIdx) {
            m_data[flatIdx(featureIdx, targetIdx)] = value;
        }

        private static int flatIdx(final int featureIdx, final int targetIdx) {
            return targetIdx * featureIdx + featureIdx;
        }

        /**
         * Normalizes the accumulated values in <b>accumulator</b>, normalizes them and updates the Shapley Values of
         * all targets for the current feature.
         *
         * @param featureIdx index of the current feature
         * @param accumulator contains the accumulated differences of all iterations for the current feature
         */
        void updateValues(final int featureIdx, final DiffAccumulator accumulator) {
            for (int i = 0; i < m_numTargets; i++) {
                final double sv = accumulator.getAccumulatedDiff(i) / m_iterationsPerFeature;
                set(sv, featureIdx, i);
            }
        }

        double[] getShapleyValues() {
            // don't clone because we use the class only privately
            return m_data;
        }

    }

    private static class DiffAccumulator {
        private final double[] m_data;

        DiffAccumulator(final int numTargets) {
            m_data = new double[numTargets];
        }

        void reset() {
            Arrays.fill(m_data, 0);
        }

        /**
         * Calculates the difference in predictions for <b>foiIntact</b> and <b>foiReplaced</b>. </br>
         * Both input parameters are expected to consist only of the predictions i.e. no features should be contained in
         * the rows.
         *
         * @param foiIntact predictions for row with the feature of interest intact
         * @param foiReplaced predictions for row with the feature of interest replaced
         */
        void update(final DataRow foiIntact, final DataRow foiReplaced) {
            final int nCells = foiIntact.getNumCells();
            assert nCells == m_data.length;
            assert nCells == foiReplaced.getNumCells();
            for (int i = 0; i < foiIntact.getNumCells(); i++) {
                m_data[i] += getPrediction(foiIntact.getCell(i)) - getPrediction(foiReplaced.getCell(i));
            }
        }

        private static double getPrediction(final DataCell cell) {
            if (cell instanceof DoubleValue) {
                final DoubleValue val = (DoubleValue)cell;
                return val.getDoubleValue();
            } else {
                // can only happen if a numerical column contains a non numerical cell i.e. never
                throw new IllegalArgumentException("A prediction column is not numerical.");
            }
        }

        double getAccumulatedDiff(final int targetIdx) {
            return m_data[targetIdx];
        }
    }

}
