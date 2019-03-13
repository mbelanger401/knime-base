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
 *   15.03.2016 (adrian): created
 */
package org.knime.base.node.meta.explain.shapley;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterPanel;

/**
 *
 * @author Adrian Nembach, KNIME.com
 * @author Simon Schmid, KNIME, Austin, USA
 */
public class ShapleyValuesLoopStartNodeDialogPane extends NodeDialogPane {

    // General components

    private final DataColumnSpecFilterPanel m_featureColumns = new DataColumnSpecFilterPanel();

    private final DataColumnSpecFilterPanel m_predictionColumns = new DataColumnSpecFilterPanel();

    private final JSpinner m_iterationsPerFeature = new JSpinner(new SpinnerNumberModel(100, 1, Integer.MAX_VALUE, 1));

    private final JSpinner m_chunkSize = new JSpinner(new SpinnerNumberModel(1000000, 1, Integer.MAX_VALUE, 1));

    private final JTextField m_seedBox = new JTextField();

    private final JButton m_newSeedBtn = new JButton("New");

    /**
     *
     */
    public ShapleyValuesLoopStartNodeDialogPane() {
        setupListeners();
        layout();
    }

    private void setupListeners() {
        m_newSeedBtn.addActionListener(this::reactToNewSeedBtnClick);
    }

    private void reactToNewSeedBtnClick(final ActionEvent e) {
        m_seedBox.setText(new Random().nextLong() + "");
    }

    private void layout() {
        // === Options Tab ===

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.gridy = 0;

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(new JLabel("Feature columns"), gbc);
        gbc.gridy += 1;
        gbc.weighty = 1;
        panel.add(m_featureColumns, gbc);
        gbc.weighty = 0;
        gbc.weightx = 0;

        gbc.gridwidth = 1;
        gbc.gridy += 1;
        panel.add(new JLabel("Prediction columns"), gbc);
        gbc.gridy++;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weighty = 1;
        panel.add(m_predictionColumns, gbc);

        addComponent(panel, gbc, "Iterations per feature", m_iterationsPerFeature);

        addComponent(panel, gbc, "Chunk size", m_chunkSize);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(new JLabel("Seed"), gbc);
        gbc.gridx++;
        gbc.weightx = 1;
        panel.add(m_seedBox, gbc);
        gbc.gridx++;
        gbc.weightx = 0;
        panel.add(m_newSeedBtn, gbc);

        addTab("Options", panel);
    }

    private static void addComponent(final JPanel panel, final GridBagConstraints gbc, final String label,
        final Component component) {
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        panel.add(new JLabel(label), gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx++;
        panel.add(component, gbc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        final ShapleyValuesSettings cfg = new ShapleyValuesSettings();
        m_featureColumns.saveConfiguration(cfg.getFeatureCols());
        m_predictionColumns.saveConfiguration(cfg.getPredictionCols());
        cfg.setIterationsPerFeature((int)m_iterationsPerFeature.getValue());
        cfg.setChunkSize((int)m_chunkSize.getValue());
        cfg.setSeed(getSeedAsLong());
        cfg.saveSettings(settings);
    }

    private long getSeedAsLong() throws InvalidSettingsException {
        final String longStr = m_seedBox.getText();
        try {
            return Long.parseLong(longStr);
        } catch (NumberFormatException e) {
            throw new InvalidSettingsException("The provided seed must be a long.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
        throws NotConfigurableException {
        final DataTableSpec inSpec = specs[0];
        final ShapleyValuesSettings cfg = new ShapleyValuesSettings();
        cfg.loadSettingsDialog(settings, inSpec);
        m_featureColumns.loadConfiguration(cfg.getFeatureCols(), inSpec);
        m_predictionColumns.loadConfiguration(cfg.getPredictionCols(), inSpec);
        m_iterationsPerFeature.setValue(cfg.getIterationsPerFeature());
        m_chunkSize.setValue(cfg.getChunkSize());
        m_seedBox.setText(cfg.getSeed() + "");
    }

}
