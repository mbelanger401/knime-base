/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2010
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
 * -------------------------------------------------------------------
 */

package org.knime.base.node.preproc.groupby.aggregation.general;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DataValue;

import org.knime.base.node.preproc.groupby.aggregation.AggregationOperator;

/**
 * Returns the last element per group.
 *
 * @author Tobias Koetter, University of Konstanz
 */
public class LastOperator extends AggregationOperator {

    private DataCell m_lastCell = null;

    /**Constructor for class MinOperator.
     * @param maxUniqueValues the maximum number of unique values
     */
    public LastOperator(final int maxUniqueValues) {
        super("Last", false, true, maxUniqueValues, DataValue.class);
    }

    /**Constructor for class LastOperator.
     * @param label user readable label
     * @param numerical <code>true</code> if the operator is only suitable
     * for numerical columns
     * @param usesLimit <code>true</code> if the method checks the number of
     * unique values limit.
     * @param keepColSpec <code>true</code> if the original column
     * specification should be kept if possible
     * @param maxUniqueValues the maximum number of unique values
     */
    public LastOperator(final String label, final boolean numerical,
            final boolean usesLimit, final boolean keepColSpec,
            final int maxUniqueValues) {
        super(label, usesLimit,  keepColSpec, maxUniqueValues,
                DataValue.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataType getDataType(final DataType origType) {
        return origType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AggregationOperator createInstance(
            final DataColumnSpec origColSpec, final int maxUniqueValues) {
        return new LastOperator(maxUniqueValues);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean computeInternal(final DataCell cell) {
        m_lastCell = cell;
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataCell getResultInternal() {
        if (m_lastCell == null) {
            return DataType.getMissingCell();
        }
        return m_lastCell;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void resetInternal() {
        m_lastCell = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Takes the last entry per group.";
    }
}