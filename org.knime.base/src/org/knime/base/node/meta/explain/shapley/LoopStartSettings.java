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

import java.util.Random;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterConfiguration;
import org.knime.core.node.util.filter.column.DataTypeColumnFilter;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
class LoopStartSettings {

    /**
     *
     */
    private static final String CFG_SEED = "seed";

    private static final String CFG_FEATURE_COLS = "featureColumns";

    private static final String CFG_PREDICTION_COLS = "predictionColumns";

    private static final String CFG_ITERATIONS_PER_FEATURE = "iterationsPerFeature";

    private static final String CFG_CHUNK_SIZE = "chunkSize";

    private static final int DEF_ITERATIONS_PER_FEATURE = 1000;

    private static final int DEF_CHUNK_SIZE = 1000;

    private int m_chunkSize = DEF_CHUNK_SIZE;

    private int m_iterationsPerFeature = DEF_ITERATIONS_PER_FEATURE;

    private long m_seed = new Random().nextLong();

    private DataColumnSpecFilterConfiguration m_featureCols = new DataColumnSpecFilterConfiguration(CFG_FEATURE_COLS);

    private DataColumnSpecFilterConfiguration m_predictionCols =
        new DataColumnSpecFilterConfiguration(CFG_PREDICTION_COLS, new DataTypeColumnFilter(DoubleValue.class));

    public void loadSettingsDialog(final NodeSettingsRO settings, final DataTableSpec inSpec) {
        m_chunkSize = settings.getInt(CFG_CHUNK_SIZE, DEF_CHUNK_SIZE);
        m_iterationsPerFeature = settings.getInt(CFG_ITERATIONS_PER_FEATURE, DEF_ITERATIONS_PER_FEATURE);
        m_featureCols = createFeatureCols();
        m_featureCols.loadConfigurationInDialog(settings, inSpec);
    }

    private static DataColumnSpecFilterConfiguration createFeatureCols() {
        return new DataColumnSpecFilterConfiguration(CFG_FEATURE_COLS);
    }

    public void loadSettingsModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_chunkSize = settings.getInt(CFG_CHUNK_SIZE);
        m_iterationsPerFeature = settings.getInt(CFG_ITERATIONS_PER_FEATURE);
        m_featureCols = createFeatureCols();
        m_featureCols.loadConfigurationInModel(settings);
        m_seed = settings.getLong(CFG_SEED);
    }

    public void saveSettings(final NodeSettingsWO settings) {
        CheckUtils.checkState(m_featureCols != null, "The config for the feature columns has not been initialized.");
        settings.addInt(CFG_CHUNK_SIZE, m_chunkSize);
        settings.addInt(CFG_ITERATIONS_PER_FEATURE, m_iterationsPerFeature);
        m_featureCols.saveConfiguration(settings);
        settings.addLong(CFG_SEED, m_seed);
    }

    /**
     * @return the chunkSize
     */
    int getChunkSize() {
        return m_chunkSize;
    }

    /**
     * @param chunkSize the chunkSize to set
     */
    void setChunkSize(final int chunkSize) {
        m_chunkSize = chunkSize;
    }

    /**
     * @return the iterationsPerFeature
     */
    int getIterationsPerFeature() {
        return m_iterationsPerFeature;
    }

    /**
     * @param iterationsPerFeature the iterationsPerFeature to set
     */
    void setIterationsPerFeature(final int iterationsPerFeature) {
        m_iterationsPerFeature = iterationsPerFeature;
    }

    /**
     * @return the featureCols
     */
    DataColumnSpecFilterConfiguration getFeatureCols() {
        return m_featureCols;
    }

    /**
     * @param featureCols the featureCols to set
     */
    void setFeatureCols(final DataColumnSpecFilterConfiguration featureCols) {
        m_featureCols = featureCols;
    }

    /**
     * @return the predictionCols
     */
    DataColumnSpecFilterConfiguration getPredictionCols() {
        return m_predictionCols;
    }

    /**
     * @param predictionCols the predictionCols to set
     */
    void setPredictionCols(final DataColumnSpecFilterConfiguration predictionCols) {
        m_predictionCols = predictionCols;
    }

    /**
     * @return the seed
     */
    long getSeed() {
        return m_seed;
    }

    /**
     * @param seed the seed to set
     */
    void setSeed(final long seed) {
        m_seed = seed;
    }

}
