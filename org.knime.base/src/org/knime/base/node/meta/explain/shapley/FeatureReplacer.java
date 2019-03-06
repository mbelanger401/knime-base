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

import org.apache.commons.math3.random.RandomDataGenerator;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class FeatureReplacer {

    private final DataRow[] m_samplingSet;

    private final RandomDataGenerator m_random;

    private final int m_featureCount;

    public FeatureReplacer(final DataRow[] samplingSet, final long seed) {
        this(samplingSet);
        m_random.getRandomGenerator().setSeed(seed);
    }

    public FeatureReplacer(final DataRow[] samplingSet) {
        CheckUtils.checkNotNull(samplingSet);
        CheckUtils.checkArgument(samplingSet.length > 0, "The sampling set may not be empty.");
        m_samplingSet = samplingSet.clone();
        m_random = new RandomDataGenerator();
        m_featureCount = m_samplingSet[0].getNumCells();
    }

    /**
     * Draws a random row from the sampling dataset and randomly replaces features in <b>row</b> with features
     * from the drawn sample
     * @param row which to transform
     * @param foi the index of the feature of interest
     * @return
     */
    public ReplacementResult replaceFeatures(final DataRow row, final int foi) {
        assert row.getNumCells() == m_samplingSet[0].getNumCells();
        int[] perm = m_random.nextPermutation(m_samplingSet.length, m_samplingSet.length);
        DataRow sampledRow = sampleRow();
        DataCell[] withFoiReplaced = getCells(row);
        DataCell[] withoutFoiReplaced = withFoiReplaced.clone();
        for (int i = 0; i < m_featureCount; i++) {
            int featureIdx = perm[i];
            DataCell replacement = sampledRow.getCell(featureIdx);
            if (featureIdx == foi) {
                withFoiReplaced[featureIdx] = replacement;
                break;
            }
            withoutFoiReplaced[featureIdx] = replacement;
        }
        return new ReplacementResult(withFoiReplaced, withoutFoiReplaced);

    }

    public int getFeatureCount() {
        return m_featureCount;
    }

    private DataCell[] getCells(final DataRow row) {
        return row.stream().toArray(DataCell[]::new);
    }

    private DataRow sampleRow() {
        return m_samplingSet[m_random.nextInt(0, m_samplingSet.length)];
    }

    static class ReplacementResult {
        private final DataCell[] m_withFoiReplaced;
        private final DataCell[] m_withoutFoiReplaced;

        ReplacementResult(final DataCell[] withFoiReplaced, final DataCell[] withoutFoiReplaced) {
            m_withFoiReplaced = withFoiReplaced;
            m_withoutFoiReplaced = withoutFoiReplaced;
        }

        DataCell[] getWithFoiReplaced() {
            return m_withFoiReplaced;
        }

        DataCell[] getWithoutFoiReplaced() {
            return m_withoutFoiReplaced;
        }
    }
}
