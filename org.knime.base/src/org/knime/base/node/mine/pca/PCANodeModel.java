/*
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2009
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 * 
 */
package org.knime.base.node.mine.pca;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.knime.base.data.append.column.AppendedColumnTable;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.port.PortObject;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

/**
 * The model class that implements the PCA on the input table.
 * 
 * @author Uwe Nagel, University of Konstanz
 */
public class PCANodeModel extends NodeModel {

    /**
     * String used for fail on missing config.
     */
    static final String FAIL_MISSING = "failMissing";

    /**
     * 
     */
    static final String INPUT_COLUMNS = "input_columns";

    /**
     * Config key, for the number of dimensions the original data is reduced to.
     */
    protected static final String RESULT_DIMENSIONS = "result_dimensions";

    /** Index of input data port. */
    public static final int DATA_INPORT = 0;

    /** Index of input data port. */
    public static final int DATA_OUTPORT = 0;

    /** number of dimensions to reduce to. */
    private final SettingsModelInteger m_reduceToDimensions =
            new SettingsModelInteger(PCANodeModel.RESULT_DIMENSIONS, 2);

    /** numeric columns to be used as input. */
    private final SettingsModelFilterString m_inputColumns =
            new SettingsModelFilterString(PCANodeModel.INPUT_COLUMNS);

    private int[] m_inputColumnIndices = {};

    /** remove original columns? */
    private final SettingsModelBoolean m_removeOriginalCols =
            new SettingsModelBoolean(REMOVE_COLUMNS, false);

    /** fail on missing data? */
    private final SettingsModelBoolean m_failOnMissingValues =
            new SettingsModelBoolean(FAIL_MISSING, false);

    private final SettingsModel[] m_settingsModels =
            {m_inputColumns, m_reduceToDimensions, m_removeOriginalCols,
                    m_failOnMissingValues};

    /**
     * description String for dimension.
     */
    static final String PCA_COL_PREFIX = "PCA dimension ";

    /** config String for remove columns. */
    static final String REMOVE_COLUMNS = "removeColumns";

    /**
     * One input, one output table.
     */
    PCANodeModel() {
        super(1, 1);

    }

    /**
     * All {@link org.knime.core.data.def.IntCell} columns are converted to
     * {@link org.knime.core.data.def.DoubleCell} columns.
     * 
     * {@inheritDoc}
     * 
     */

    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {

        if (m_inputColumns.getIncludeList().size() == 0) {
            m_inputColumnIndices = getDefaultColumns(inSpecs[DATA_INPORT]);
            m_reduceToDimensions.setIntValue(m_inputColumnIndices.length);
            setWarningMessage("using as default all possible columns ("
                    + m_inputColumnIndices.length + ") for PCA!");
        }
        m_inputColumnIndices = new int[m_inputColumns.getIncludeList().size()];
        int colIndex = 0;
        for (final String colName : m_inputColumns.getIncludeList()) {
            final DataColumnSpec colspec =
                    inSpecs[DATA_INPORT].getColumnSpec(colName);
            if (colspec == null) {
                m_inputColumnIndices = getDefaultColumns(inSpecs[DATA_INPORT]);
                m_reduceToDimensions.setIntValue(m_inputColumnIndices.length);
                setWarningMessage("using as default all possible columns ("
                        + m_inputColumnIndices.length + ") for PCA!");
                break;
            } else if (!colspec.getType().isCompatible(DoubleValue.class)) {
                throw new InvalidSettingsException("column \"" + colName
                        + "\" is not compatible with double");
            }
            m_inputColumnIndices[colIndex++] =
                    inSpecs[DATA_INPORT].findColumnIndex(colName);
        }
        final DataColumnSpec[] specs =
                createAddTableSpec(inSpecs[DATA_INPORT], m_reduceToDimensions
                        .getIntValue());
        final DataTableSpec dts =
                AppendedColumnTable.getTableSpec(inSpecs[0], specs);
        if (m_removeOriginalCols.getBooleanValue()) {
            final ColumnRearranger columnRearranger = new ColumnRearranger(dts);
            columnRearranger.remove(m_inputColumnIndices);
            return new DataTableSpec[]{columnRearranger.createSpec()};
        }

        return new DataTableSpec[]{dts};

    }

    /**
     * get column indices for all double compatible columns.
     * 
     * @param dataTableSpec table spec
     * @return array of indices
     */
    static int[] getDefaultColumns(final DataTableSpec dataTableSpec) {
        final LinkedList<Integer> cols = new LinkedList<Integer>();
        for (int i = 0; i < dataTableSpec.getNumColumns(); i++) {
            if (dataTableSpec.getColumnSpec(i).getType().isCompatible(
                    DoubleValue.class)) {
                cols.add(i);
            }
        }
        final int[] ret = new int[cols.size()];
        int i = 0;
        for (final int t : cols) {
            ret[i++] = t;
        }
        return ret;
    }

    /**
     * create part of table spec to be added to the input table.
     * 
     * @param inSpecs inpecs (for unique column names)
     * @param resultDimensions TODO
     * @return part of table spec to be added to input table
     */
    public static DataColumnSpec[] createAddTableSpec(
            final DataTableSpec inSpecs, final int resultDimensions) {
        // append pca columns
        final DataColumnSpec[] specs = new DataColumnSpec[resultDimensions];

        for (int i = 0; i < resultDimensions; i++) {
            final String colName =
                    DataTableSpec.getUniqueColumnName(inSpecs, PCA_COL_PREFIX
                            + i);
            final DataColumnSpecCreator specCreator =
                    new DataColumnSpecCreator(colName, DataType
                            .getType(DoubleCell.class));
            specs[i] = specCreator.createSpec();
        }
        return specs;
    }

    /**
     * Performs the PCA.
     * 
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inData,
            final ExecutionContext exec) throws Exception {

        // remove all non-numeric columns from the input date
        // final DataTable filteredTable =
        // filterNonNumericalColumns(inData[DATA_INPORT]);
        final int dimensions = m_reduceToDimensions.getIntValue();
        // adjust to selected numerical columns
        if (dimensions > m_inputColumnIndices.length || dimensions < 1) {
            throw new IllegalArgumentException(
                    "invalid number of dimensions to reduce to: " + dimensions);
        }

        final BufferedDataTable dataTable =
                (BufferedDataTable)inData[DATA_INPORT];
        if (dataTable.getRowCount() == 0) {
            throw new IllegalArgumentException("Input table is empty!");
        }
        if (dataTable.getRowCount() == 1) {
            throw new IllegalArgumentException("Input table has only one row!");
        }

        final double[] meanVector =
                getMeanVector(dataTable, m_inputColumnIndices, false);
        final double[][] m =
                new double[m_inputColumnIndices.length][m_inputColumnIndices.length];
        final int missingValues =
                getCovarianceMatrix(exec.createSubExecutionContext(0.5),
                        dataTable, m_inputColumnIndices, meanVector, m);
        final Matrix covarianceMatrix = new Matrix(m);
        if (missingValues > 0) {
            if (m_failOnMissingValues.getBooleanValue()) {
                throw new IllegalArgumentException(
                        "missing, infinite or impossible values in table");
            }
            setWarningMessage(missingValues
                    + " rows ignored because of missing"
                    + ", infinite or impossible values");

        }
        exec.setMessage("computing spectral decomposition");
        final EigenvalueDecomposition eig = covarianceMatrix.eig();
        final double[] evs = EigenValue.extractEVVector(eig);
        final Matrix eigenvectors =
                EigenValue.getSortedEigenVectors(eig.getV().getArray(), evs,
                        dimensions);
        eigenvectors.transpose();
        final double postDecompositionProgress = 0.7;
        exec.setProgress(postDecompositionProgress);
        final DataColumnSpec[] specs =
                createAddTableSpec(
                        (DataTableSpec)inData[DATA_INPORT].getSpec(),
                        m_reduceToDimensions.getIntValue());

        final CellFactory fac = new CellFactory() {

            @Override
            public DataCell[] getCells(final DataRow row) {
                return convertInputRow(eigenvectors, row, meanVector,
                        m_inputColumnIndices, m_reduceToDimensions
                                .getIntValue(), false);
            }

            @Override
            public DataColumnSpec[] getColumnSpecs() {

                return specs;
            }

            @Override
            public void setProgress(final int curRowNr, final int rowCount,
                    final RowKey lastKey, final ExecutionMonitor texec) {
                texec.setProgress(postDecompositionProgress
                        + (1d - postDecompositionProgress) * curRowNr
                        / rowCount, "processing " + curRowNr + " of "
                        + rowCount);

            }

        };

        final ColumnRearranger cr =
                new ColumnRearranger((DataTableSpec)inData[0].getSpec());
        cr.append(fac);
        if (m_removeOriginalCols.getBooleanValue()) {
            cr.remove(m_inputColumnIndices);
        }
        final BufferedDataTable result =
                exec.createColumnRearrangeTable((BufferedDataTable)inData[0],
                        cr, exec);
        final PortObject[] out = {result};
        return out;
    }

    /**
     * reduce a single input row to the principal components.
     * 
     * @param eigenvectors transposed matrix of eigenvectors (eigenvectors in
     *            rows, number of eigenvectors corresponds to dimensions to be
     *            projected to)
     * @param row the row to convert
     * @param means mean values of the columns
     * @param inputColumnIndices indices of the input columns
     * @param resultDimensions number of dimensions to project to
     * @param failOnMissing throw exception if missing values are encountered
     * @return array of data cells to be added to the row
     */
    protected static DataCell[] convertInputRow(final Matrix eigenvectors,
            final DataRow row, final double[] means,
            final int[] inputColumnIndices, final int resultDimensions,
            final boolean failOnMissing) {
        // get row of input values
        boolean missingValues = false;
        for (int i = 0; i < inputColumnIndices.length; i++) {
            if (row.getCell(inputColumnIndices[i]).isMissing()) {
                missingValues = true;
                continue;
            }
        }
        if (missingValues && failOnMissing) {
            throw new IllegalArgumentException("table contains missing values");
        }
        // put each cell of a pca row into the row to append
        final DataCell[] cells = new DataCell[inputColumnIndices.length];

        if (missingValues) {
            for (int i = 0; i < resultDimensions; i++) {
                cells[i] = DataType.getMissingCell();
            }
        } else {
            final double[][] rowVec = new double[inputColumnIndices.length][1];
            for (int i = 0; i < rowVec.length; i++) {

                rowVec[i][0] =
                        ((DoubleValue)row.getCell(inputColumnIndices[i]))
                                .getDoubleValue()
                                - means[i];

            }
            final double[][] newRow =
                    new Matrix(rowVec).transpose().times(eigenvectors)
                            .getArray();

            for (int i = 0; i < resultDimensions; i++) {
                cells[i] = new DoubleCell(newRow[0][i]);
            }
        }
        return cells;
    }

    /**
     * Converts a {@link DataTable} to the 2D-double array representing its
     * covariance matrix. Only numeric attributes are included.
     * 
     * @param exec the execution context for progress report (a subcontext)
     * 
     * @param dataTable the {@link DataTable} to convert
     * @param numericIndices indices of input columns
     * @param means mean values of columns
     * @param dataMatrix matrix to write covariances to
     * @return number of ignored rows (containing missing values)
     * @throws CanceledExecutionException if execution is canceled
     */
    static int getCovarianceMatrix(final ExecutionContext exec,
            final BufferedDataTable dataTable, final int[] numericIndices,
            final double[] means, final double[][] dataMatrix)
            throws CanceledExecutionException {

        // create result 2-D array
        // fist dim corresponds to the rows, second dim to columns

        int counter = 0;
        int missingCount = 0;
        // for all rows
        ROW: for (final DataRow row : dataTable) {
            // ignore rows with missing cells
            for (int i = 0; i < numericIndices.length; i++) {
                if (row.getCell(numericIndices[i]).isMissing()) {
                    missingCount++;
                    continue ROW;
                }
                if (!row.getCell(numericIndices[i]).getType().isCompatible(
                        DoubleValue.class)) {
                    throw new IllegalArgumentException("column "
                            + dataTable.getSpec().getColumnSpec(
                                    numericIndices[i]).getName()
                            + " has incompatible type!");
                }
                final double val =
                        ((DoubleValue)row.getCell(numericIndices[i]))
                                .getDoubleValue();
                if (Double.isInfinite(val) || Double.isNaN(val)) {
                    missingCount++;
                    continue ROW;
                }
            }
            // for all valid attributes
            for (int i = 0; i < numericIndices.length; i++) {
                for (int j = 0; j < numericIndices.length; j++) {

                    dataMatrix[i][j] +=
                            (((DoubleValue)row.getCell(numericIndices[i]))
                                    .getDoubleValue() - means[i])

                                    * (((DoubleValue)row
                                            .getCell(numericIndices[j]))
                                            .getDoubleValue() - means[j]);
                    if (Double.isInfinite(dataMatrix[i][j])
                            || Double.isNaN(dataMatrix[i][j])) {
                        throw new IllegalArgumentException(
                                "computation failed for numerical problems"
                                        + ", probably some numbers are to huge");
                    }
                }
            }
            counter++;
            exec.setProgress((double)counter / dataTable.getRowCount(),
                    "processing row " + counter + " of "
                            + dataTable.getRowCount());
            exec.checkCanceled();
        }
        if (counter < 2) {
            new IllegalArgumentException(
                    "Input table has to few rows with valid values!");
        }
        for (int i = 0; i < dataMatrix.length; i++) {
            for (int j = 0; j < dataMatrix[i].length; j++) {
                dataMatrix[i][j] /= counter;
            }
        }
        return missingCount;
    }

    /**
     * calculate means of all columns.
     * 
     * @param dataTable input table
     * @param numericIndices indices of columns to use
     * @param failOnMissingValues if true, throw exception if missing values are
     *            encountered
     * @return vector of column mean values
     */
    static double[] getMeanVector(final DataTable dataTable,
            final int[] numericIndices, final boolean failOnMissingValues) {
        final double[] means = new double[numericIndices.length];
        int numRows = 0;
        // calculate mean for each row and column
        ROW: for (final DataRow row : dataTable) {
            // ignore rows with missing cells
            for (int i = 0; i < numericIndices.length; i++) {
                if (row.getCell(numericIndices[i]).isMissing()) {
                    if (failOnMissingValues) {
                        throw new IllegalArgumentException(
                                "missing values in table");
                    }
                    continue ROW;
                }
                final double val =
                        ((DoubleValue)row.getCell(numericIndices[i]))
                                .getDoubleValue();
                if (Double.isInfinite(val) || Double.isNaN(val)) {

                    continue ROW;
                }
            }

            int i = 0;
            for (final Integer index : numericIndices) {
                means[i++] +=
                        ((DoubleValue)row.getCell(index)).getDoubleValue();
            }
            numRows++;
        }
        for (int i = 0; i < means.length; i++) {
            means[i] /= numRows;
        }
        return means;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        for (final SettingsModel s : this.m_settingsModels) {
            s.loadSettingsFrom(settings);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        m_inputColumnIndices = new int[]{};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        for (final SettingsModel s : this.m_settingsModels) {
            s.saveSettingsTo(settings);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        for (final SettingsModel s : this.m_settingsModels) {
            s.validateSettings(settings);
        }
    }
}