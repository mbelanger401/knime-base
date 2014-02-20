/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by 
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
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
 * ---------------------------------------------------------------------
 *
 * Created on 2013.06.13. by Gabor
 */
package org.knime.base.data.statistics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;

/**
 * Finds the median for selected ({@link DoubleValue}d) columns.
 *
 * @author Gabor Bakos
 * @since 2.8
 */
class MedianTable {

    private final BufferedDataTable m_table;

    private final int[] m_indices;

    private final boolean m_includeNaNs;

    private final boolean m_includeMissingValues;

    private double[] m_medians;

    private boolean m_inMemory = false;

    /**
     * @param table The input table.
     * @param indices The unique column indices denoting only {@link DoubleValue}d columns within {@code table}.
     */
    public MedianTable(final BufferedDataTable table, final int[] indices) {
        this(table, indices, true, false);
    }

    /**
     * @param table The input table.
     * @param colNames The unique column names denoting only {@link DoubleValue}d columns within {@code table}.
     * @param includeNaNs Include the {@link Double#NaN} values to the possible values?
     * @param includeMissingValues Include the missing values to the number of values?
     */
    public MedianTable(final BufferedDataTable table, final List<String> colNames, final boolean includeNaNs,
        final boolean includeMissingValues) {
        this(table, findIndices(table, colNames), includeNaNs, includeMissingValues);
    }

    /**
     * @param table The input table.
     * @param colNames The unique column names denoting only {@link DoubleValue}d columns within {@code table}.
     * @return The column indices of {@code colNames} within {@code table}.
     */
    private static int[] findIndices(final BufferedDataTable table, final List<String> colNames) {
        final DataTableSpec spec = table.getSpec();
        if (new HashSet<String>(colNames).size() != colNames.size()) {
            throw new IllegalArgumentException("Same column name multiple times: " + colNames);
        }
        int[] ret = new int[colNames.size()];
        for (int i = colNames.size(); i-- > 0;) {
            ret[i] = spec.findColumnIndex(colNames.get(i));
        }
        return ret;
    }

    /**
     * @param table The input table.
     * @param indices The unique column indices denoting only {@link DoubleValue}d columns within {@code table}.
     * @param includeNaNs Include the {@link Double#NaN} values to the possible values?
     * @param includeMissingValues Include the missing values to the number of values?
     */
    public MedianTable(final BufferedDataTable table, final int[] indices, final boolean includeNaNs,
        final boolean includeMissingValues) {
        this.m_table = table;
        this.m_indices = indices.clone();
        this.m_includeNaNs = includeNaNs;
        this.m_includeMissingValues = includeMissingValues;
    }

    /**
     * @param context An {@link ExecutionContext}
     * @return The median values for the columns in the order of the columns specified in the constructor. The values
     *         can be {@link Double#NaN}s in certain circumstances.
     * @throws CanceledExecutionException When cancelled.
     */
    public synchronized double[] medianValues(final ExecutionContext context) throws CanceledExecutionException {
        if (m_medians == null) {
            m_medians = new double[m_indices.length];
            int[] validCount = new int[m_indices.length];
            for (DataRow row : m_table) {
                context.checkCanceled();
                for (int i = 0; i < m_indices.length; ++i) {
                    int col = m_indices[i];
                    final DataCell cell = row.getCell(col);
                    if (cell.isMissing()) {
                        if (m_includeMissingValues) {
                            validCount[i]++;
                        }
                    } else if (cell instanceof DoubleValue) {
                        DoubleValue dv = (DoubleValue)cell;
                        if (m_includeNaNs) {
                            validCount[i]++;
                        } else if (!Double.isNaN(dv.getDoubleValue())) {
                            validCount[i]++;
                        }
                    } else {
                        throw new IllegalStateException("Not a double value: " + cell + " in column: "
                            + m_table.getSpec().getColumnSpec(col).getName());
                    }
                }
            }
            List<String> incList = new ArrayList<String>(m_indices.length);
            final String[] columnNames = m_table.getSpec().getColumnNames();
            for (int i : m_indices) {
                incList.add(columnNames[i]);
            }

            int[][] k = new int[2][m_indices.length];
            for (int i = 0; i < 2; ++i) {
                for (int j = m_indices.length; j-- > 0;) {
                    k[i][j] = validCount[j] > 0 ? (validCount[j] - 1 + i) / 2 : 0;
                }
            }
            BufferedSelectRank selectRank = new BufferedSelectRank(m_table, incList, k);
            selectRank.setSortInMemory(m_inMemory);
            BufferedDataTable dataTable = selectRank.select(context);
            final CloseableRowIterator iterator = dataTable.iterator();
            try {
                DataRow row1 = iterator.next();
                DataRow row2 = iterator.next();
                for (int i = 0; i < m_indices.length; ++i) {
                    final DataCell cell1 = row1.getCell(i);
                    final DataCell cell2 = row2.getCell(i);
                    if (cell1 instanceof DoubleValue && cell2 instanceof DoubleValue) {
                        DoubleValue dv1 = (DoubleValue)cell1;
                        DoubleValue dv2 = (DoubleValue)cell2;
                        m_medians[i] = (dv1.getDoubleValue() + dv2.getDoubleValue()) / 2;
                    } else {
                        m_medians[i] = Double.NaN;
                    }
                }
            } finally {
                iterator.close();
            }
        }
        return m_medians.clone();
    }

    /**
     * @param inMemory the inMemory to set
     */
    public synchronized void setInMemory(final boolean inMemory) {
        this.m_inMemory = inMemory;
    }
}
