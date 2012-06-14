/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2011
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

package org.knime.base.data.aggregation;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;


/**
 * Utility class that contains general information such as the
 * column delimiter and the total number of rows.
 * The informations might be provided by the user in the node dialog.
 *
 * @author Tobias Koetter, University of Konstanz
 */
public class GlobalSettings {

    /**Default global settings object used in operator templates.*/
    public static final GlobalSettings DEFAULT = new GlobalSettings();

    /**The standard delimiter used in concatenation operators.*/
    public static final String STANDARD_DELIMITER = ", ";

    /**The maximum number of unique values. the threshold is used
     * if the method uses a limit.*/
    private final int m_maxUniqueValues;

    /**The delimiter to use for value separation.*/
    private final String m_valueDelimiter;

    private final DataTableSpec m_spec;

    private final int m_noOfRows;

    /**Constructor for class GlobalSettings.
     * This constructor is used to create a dummy object that contains
     * default settings.
     */
    GlobalSettings() {
        this(0);
    }

    /**Constructor for class GlobalSettings that uses the standard
     * value delimiter.
     *
     * @param maxUniqueValues the maximum number of unique values to consider
     */
    public GlobalSettings(final int maxUniqueValues) {
        this(maxUniqueValues, STANDARD_DELIMITER, new DataTableSpec(), 0);
    }

    /**Constructor for class GlobalSettings.
     * @param maxUniqueValues the maximum number of unique values to consider
     * @param valueDelimiter the delimiter to use for value separation
     * @param spec the {@link DataTableSpec} of the input table
     * @param noOfRows the number of rows of the input table
     */
    public GlobalSettings(final int maxUniqueValues,
            final String valueDelimiter, final DataTableSpec spec,
            final int noOfRows) {
        if (maxUniqueValues < 0) {
            throw new IllegalArgumentException(
                    "Maximum unique values must be a positive integer");
        }
        if (valueDelimiter == null) {
            throw new NullPointerException(
                    "Value delimiter should not be null");
        }
        if (spec == null) {
            throw new NullPointerException("spec must not be null");
        }
        if (noOfRows < 0) {
            throw new IllegalArgumentException("No of rows must be positive");
        }
        m_maxUniqueValues = maxUniqueValues;
        m_valueDelimiter = valueDelimiter;
        m_spec = spec;
        m_noOfRows = noOfRows;
    }

    /**
     * @return the maximum number of unique values to consider
     */
    public int getMaxUniqueValues() {
        return m_maxUniqueValues;
    }

    /**
     * @return the standard delimiter to use for value separation
     */
    public String getValueDelimiter() {
        return m_valueDelimiter;
    }


    /**
     * @return the total number of rows of the input table
     */
    public int getNoOfRows() {
        return m_noOfRows;
    }

    /**
     * Returns the number of columns of the original input table.
     *
     * @return the number of columns
     */
    public int getNoOfColumns() {
        return m_spec.getNumColumns();
    }

    /**
     * Finds the column with the specified name in the TableSpec of the
     * original input table and returns its index, or -1 if the name
     * doesn't exist in the table. This method returns
     * -1 if the argument is <code>null</code>.
     *
     * @param columnName the name to search for
     * @return the index of the column with the specified name, or -1 if not
     *         found.
     */
    public int findColumnIndex(final String columnName) {
        return m_spec.findColumnIndex(columnName);
    }

    /**
     * Returns column information of the original column with
     * the provided index.
     *
     * @param index the column index within the table
     * @return the column specification
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     */
    public DataColumnSpec getOriginalColumnSpec(final int index) {
        return m_spec.getColumnSpec(index);
    }

    /**
     * Returns the {@link DataColumnSpec} of the original column with the
     * provided name.
     * This method returns <code>null</code> if the argument is
     * <code>null</code>.
     *
     * @param column the column name to find the spec for
     * @return the column specification or <code>null</code> if not available
     */
    public DataColumnSpec getOriginalColumnSpec(final String column) {
        return m_spec.getColumnSpec(column);
    }
}