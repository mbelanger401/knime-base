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
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowIterator;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
class ShapleyValuesEstimator {

    /**
     *
     */
    private static final double PROG_FRAC_SAMPLING_CREATION = 0.5;

    private static final double PROG_FRAC_PERTURB_ROWS = 0.5;

    private ShapleyValuesAlgorithm m_algorithm;

    private final TablePreparer m_tablePreparer;

    private ShapleyValuesSettings m_settings;

    public ShapleyValuesEstimator(final ShapleyValuesSettings settings) {
        m_tablePreparer = new TablePreparer(settings.getFeatureCols(), settings.getPredictionCols());
        m_settings = settings;
    }

    private void initializeAlgorithm(final BufferedDataTable samplingTable, final ExecutionMonitor prog)
        throws Exception {
        final DataRow[] samplingSet = createSamplingSet(samplingTable, prog);
        final FeatureReplacer fr = new FeatureReplacer(samplingSet, m_settings.getSeed());
        m_algorithm = new ShapleyValuesAlgorithm(fr, m_settings.getIterationsPerFeature(),
            m_tablePreparer.getNumPredictionColumns());
    }

    public BufferedDataTable executeLoopStart(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        initializeAlgorithm(samplingTable, exec.createSubProgress(PROG_FRAC_SAMPLING_CREATION));
        return perturbRows(roiTable, exec.createSubExecutionContext(PROG_FRAC_PERTURB_ROWS));
    }

    /**
     * @param roiTable
     * @param exec
     * @return
     * @throws InvalidSettingsException
     */
    private BufferedDataTable perturbRows(final BufferedDataTable roiTable, final ExecutionContext exec)
        throws Exception {
        exec.setMessage("Perturb rows");
        final RowIterator roiIterator = getRoiIterator(roiTable);
        final double total = roiTable.size();
        long current = 0;

        final BufferedDataContainer container = exec.createDataContainer(m_tablePreparer.getLoopStartSpec());

        while (roiIterator.hasNext()) {
            exec.checkCanceled();
            final DataRow row = roiIterator.next();
            exec.setProgress(current / total, "Perturb row " + row.getKey());
            current++;
            for (DataRow perturbed : m_algorithm.prepareRow(row)) {
                container.addRowToTable(perturbed);
            }
        }
        container.close();

        return container.getTable();
    }

    public DataTableSpec configureLoopStart(final DataTableSpec roiSpec, final DataTableSpec samplingSpec,
        final ShapleyValuesSettings settings) throws InvalidSettingsException {
        m_tablePreparer.updateSpecs(roiSpec, settings.getFeatureCols(), settings.getPredictionCols());
        m_tablePreparer.checkSamplingSpec(samplingSpec);
        return m_tablePreparer.getLoopStartSpec();
    }

    public BufferedDataTable executeLoopEnd(final BufferedDataTable predictedTable, final ExecutionContext exec)
        throws Exception {
        exec.setMessage("Calculating Shapley Values.");
        final double total = predictedTable.size();
        long current = 0;
        final DataTableSpec outputSpec = m_tablePreparer.getLoopEndSpec(predictedTable.getDataTableSpec());
        final RowIterator iter = getPredictionIterator(predictedTable);
        final BufferedDataContainer container = exec.createDataContainer(outputSpec);
        while (iter.hasNext()) {
            exec.checkCanceled();
            final DataRow row = m_algorithm.calculateShapleyValuesForNextRow(iter);
            exec.setProgress(current / total, "Finished Shapley Value calculation for row " + row.getKey());
            container.addRowToTable(row);
            current++;
        }
        container.close();
        return container.getTable();
    }

    public DataTableSpec createLoopEndSpec(final DataTableSpec inSpec) throws InvalidSettingsException {
        return m_tablePreparer.getLoopEndSpec(inSpec);
    }

    private RowIterator getPredictionIterator(final DataTable predictionTable) throws InvalidSettingsException {
        final DataTable filtered = m_tablePreparer.prepareTableForEvaluation(predictionTable);
        return filtered.iterator();
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
    private DataRow[] createSamplingSet(final BufferedDataTable samplingTable, final ExecutionMonitor prog)
        throws Exception {
        prog.setProgress("Create sampling dataset");
        final DataTable filtered = m_tablePreparer.prepareTableForPerturbation(samplingTable);
        final List<DataRow> samplingData = new ArrayList<>();
        long current = 0;
        final double total = samplingTable.size();
        for (DataRow row : filtered) {
            prog.checkCanceled();
            prog.setProgress(current / total, "Reading row " + row.getKey());
            current++;
            samplingData.add(row);
        }
        return samplingData.toArray(new DataRow[samplingData.size()]);
    }

}
