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
 *   08.03.2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shapley;

import org.knime.base.data.filter.column.FilterColumnTable;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterConfiguration;

/**
 * Ensures that the specs and tables passed on to the estimator have the correct structure.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
class TablePreparer {

    private final ColumnSetManager m_featureColManager;

    private final ColumnSetManager m_predictionColManager;

    public TablePreparer(final DataColumnSpecFilterConfiguration featureFilter,
        final DataColumnSpecFilterConfiguration predictionFilter) {
        m_featureColManager = new ColumnSetManager(featureFilter);
        m_predictionColManager = new ColumnSetManager(predictionFilter);
    }

    /**
     * Creates the output spec for the loop start node.
     *
     * @param inSpec input spec of the loop start node
     * @return output spec of the loop start node
     */
    public DataTableSpec getLoopStartSpec(final DataTableSpec inSpec) {
        m_featureColManager.updateColumnSet(inSpec);
        m_predictionColManager.updateColumnSet(inSpec);
        return m_featureColManager.getTableSpec();
    }

    /**
     * Creates the output spec for the loop end node.
     *
     * @param inSpec the input table spec to the loop end
     *
     * @return output spec of the loop end node
     * @throws InvalidSettingsException if a prediction column is missing or has the wrong type
     */
    public DataTableSpec getLoopEndSpec(final DataTableSpec inSpec) throws InvalidSettingsException {
        ensurePredictionColumnsAreValid(inSpec);
        final DataTableSpecCreator dtsc = new DataTableSpecCreator();
        final DataColumnSpec[] predCols = m_predictionColManager.getColumns();
        final DataColumnSpec[] featureCols = m_featureColManager.getColumns();
        for (final DataColumnSpec predCol : predCols) {
            for (final DataColumnSpec featureCol : featureCols) {
                final String colName = createColumnName(predCol.getName(), featureCol.getName());
                final DataColumnSpecCreator c = new DataColumnSpecCreator(colName, DoubleCell.TYPE);
                dtsc.addColumns(c.createSpec());
            }
        }
        return dtsc.createSpec();
    }

    private void ensurePredictionColumnsAreValid(final DataTableSpec inSpec) throws InvalidSettingsException {
        ensureColumnsAreContained(inSpec, m_predictionColManager, "prediction");
    }

    private static void ensureColumnsAreContained(final DataTableSpec inSpec, final ColumnSetManager mgr,
        final String mgrPurpose) throws InvalidSettingsException {
        if (!mgr.containsColumns(inSpec)) {
            throw new InvalidSettingsException("The input table does not contain all " + mgrPurpose + " columns.");
        }
    }

    private static String createColumnName(final String predictionCol, final String featureCol) {
        return featureCol + "(" + predictionCol + ")";
    }

    /**
     * Removes all non-feature columns (including the prediction columns)
     *
     * @param table to prepare for perturbation
     * @return table containing only the feature columns
     * @throws InvalidSettingsException if <B>table</b> does not contain all feature columns
     */
    public DataTable prepareTableForPerturbation(final DataTable table) throws InvalidSettingsException {
        ensureColumnsAreContained(table.getDataTableSpec(), m_featureColManager, "feature");
        return filterTable(table, m_featureColManager.getTableSpec());
    }

    /**
     * Removes all non-prediction columns (including the feature columns)
     *
     * @param table to prepare for evaluation
     * @return table containing only the prediction columns
     * @throws InvalidSettingsException if <b>table</b> does not contain all prediction columns
     */
    public DataTable prepareTableForEvaluation(final DataTable table) throws InvalidSettingsException {
        final DataTableSpec tableSpec = table.getDataTableSpec();
        ensurePredictionColumnsAreValid(tableSpec);
        return filterTable(table, m_predictionColManager.getTableSpec());
    }

    public int getNumPredictionColumns() {
        return m_predictionColManager.getNumColumns();
    }

    private static DataTable filterTable(final DataTable table, final DataTableSpec desiredSpec) {
        final DataTableSpec incomingSpec = table.getDataTableSpec();
        final int[] includes =
            desiredSpec.stream().map(DataColumnSpec::getName).mapToInt(incomingSpec::findColumnIndex).toArray();
        return new FilterColumnTable(table, true, includes);
    }

}
