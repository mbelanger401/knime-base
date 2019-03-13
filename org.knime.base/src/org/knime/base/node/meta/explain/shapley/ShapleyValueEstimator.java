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
 *   13.03.2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shapley;

import java.util.ArrayList;
import java.util.List;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.RowIterator;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
class ShapleyValueEstimator {

    private ShapleyValueAlgorithm m_algorithm;

    private final TablePreparer m_tablePreparer;

    private final LoopStartSettings m_settings;

    public ShapleyValueEstimator(final LoopStartSettings settings) {
        m_tablePreparer = new TablePreparer(settings.getFeatureCols(), settings.getPredictionCols());
        m_settings = settings;
    }

    private void initializeAlgorithm(final DataTable samplingTable) throws InvalidSettingsException {
        final DataRow[] samplingSet = createSamplingSet(samplingTable);
        final FeatureReplacer fr = new FeatureReplacer(samplingSet);
        m_algorithm = new ShapleyValueAlgorithm(fr, m_settings.getIterationsPerFeature(),
            m_tablePreparer.getNumPredictionColumns());
    }

    public BufferedDataTable executeLoopStart(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        initializeAlgorithm(samplingTable);
        return perturbRows(roiTable, exec);
    }

    /**
     * @param roiTable
     * @param exec
     * @return
     * @throws InvalidSettingsException
     */
    private BufferedDataTable perturbRows(final BufferedDataTable roiTable, final ExecutionContext exec)
        throws InvalidSettingsException {
        final RowIterator roiIterator = getRoiIterator(roiTable);

        final BufferedDataContainer container =
            exec.createDataContainer(m_tablePreparer.getLoopStartSpec(roiTable.getDataTableSpec()));

        while (roiIterator.hasNext()) {
            for (DataRow perturbed : m_algorithm.prepareRow(roiIterator.next())) {
                container.addRowToTable(perturbed);
            }
        }
        container.close();

        return container.getTable();
    }

    private RowIterator getRoiIterator(final DataTable roiTable) throws InvalidSettingsException {
        final DataTable filtered = m_tablePreparer.prepareTableForPerturbation(roiTable);
        return filtered.iterator();
    }

    /**
     * @param samplingTable
     * @return
     * @throws InvalidSettingsException
     */
    private DataRow[] createSamplingSet(final DataTable samplingTable) throws InvalidSettingsException {
        final DataTable filtered = m_tablePreparer.prepareTableForPerturbation(samplingTable);
        final List<DataRow> samplingData = new ArrayList<>();
        for (DataRow row : filtered) {
            samplingData.add(row);
        }
        return samplingData.toArray(new DataRow[samplingData.size()]);
    }

}
