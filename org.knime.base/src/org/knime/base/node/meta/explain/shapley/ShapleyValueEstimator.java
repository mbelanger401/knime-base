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
import org.knime.core.data.DataRow;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class ShapleyValueEstimator {

    private final FeatureReplacer m_featureReplacer;

    private final int m_iterationsPerFeature;

    private final int m_numTargetCols;

    public ShapleyValueEstimator(final FeatureReplacer featureReplacer, final int iterationsPerFeature,
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
        List<DataRow> rows = new ArrayList<>(2 * row.getNumCells() * m_iterationsPerFeature);
        RowKeyGenerator keyGen = new RowKeyGenerator(row.getKey());
        for (int i = 0; i < row.getNumCells() - m_numTargetCols; i++) {
            for (int j = 0; j < m_iterationsPerFeature; j++) {
                ReplacementResult r = m_featureReplacer.replaceFeatures(row, i);
                rows.add(new DefaultRow(keyGen.createKey(i, j, true), r.getWithFoiReplaced()));
                rows.add(new DefaultRow(keyGen.createKey(i, j, false), r.getWithoutFoiReplaced()));
            }
        }
        return rows;
    }

    double[] calculateShapleyValuesForNextRow(final Iterator<DataRow> rows) {
        DataRow currentRow = rows.next();
        final String originalKey = RowKeyGenerator.parseOriginalKey(currentRow.getKey());
        final int featureCount = m_featureReplacer.getFeatureCount();
        double[] shapleyValues = new double[featureCount];
        for (int i = 0; i < featureCount; i++) {
            double currentShapleyValue = 0.0;
            for (int j = 0; j < m_iterationsPerFeature; j++) {
                currentShapleyValue += getDifferenceInPredictions(currentRow, rows.next());
                if (rows.hasNext()) {
                    currentRow = rows.next();
                } else {
                    CheckUtils.checkState(i == featureCount - 1, "Not all transformed rows arrived in the loop end.");
                    CheckUtils.checkState(j == m_iterationsPerFeature - 1,
                        "Not all transformed rows arrived in the loop end.");
                }
            }
            shapleyValues[i] = currentShapleyValue / m_iterationsPerFeature;
        }
        return shapleyValues;
    }

    private double getDifferenceInPredictions(final DataRow withFoiReplaced, final DataRow withoutFoiReplaced) {
        // TODO
        return 0;
    }

    private static class RowKeyGenerator {
        /**
         *
         */
        private static final String DELIMITER = "_";
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
            sb.append(withFoiReplaced ? "o" : "i");
            return new RowKey(sb.toString());
        }

        static boolean isStartOfRowBatch(final RowKey key) {
            String[] split = key.getString().split(DELIMITER);
            CheckUtils.checkArgument(split.length >= 4, createErrorString(key));

            // TODO
            return false;
        }


        static String parseOriginalKey(final RowKey generatedKey) {
            String[] split = split(generatedKey);
            return Arrays.stream(split).limit(split.length - 3l).collect(Collectors.joining(DELIMITER));
        }

        private static String[] split(final RowKey generatedKey) {
            String[] split = generatedKey.getString().split(DELIMITER);
            CheckUtils.checkArgument(split.length >= 4, createErrorString(generatedKey));
            return split;
        }

        static int parseFeatureIdx(final RowKey generatedKey) {
            String[] split = generatedKey.getString().split(DELIMITER);
            CheckUtils.checkArgument(split.length < 4, createErrorString(generatedKey));
            int idxOfFeatureIdx = split.length - 3;
            int featureIdx = -1;
            try {
                featureIdx = Integer.parseInt(split[idxOfFeatureIdx]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(createErrorString(generatedKey));
            }
            CheckUtils.checkArgument(featureIdx >= 0, createErrorString(generatedKey));
            return featureIdx;
        }

        private static String createErrorString(final RowKey generatedKey) {
            return "The row key '" + generatedKey + "' was not created by an instance of this RowKeyGenerator.";
        }
    }

}
