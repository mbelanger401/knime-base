/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2013
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
 *
 * History
 *   27.07.2005 (mb): created
 */
package org.knime.base.node.mine.decisiontree2.predictor2;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.base.node.mine.util.PredictorHelper;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeView;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.pmml.PMMLPortObjectSpec;

/**
 *
 * @author Michael Berthold, University of Konstanz
 * @since 2.9
 */
public class DecTreePredictorNodeFactory
        extends NodeFactory<DecTreePredictorNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public DecTreePredictorNodeModel createNodeModel() {
        return new DecTreePredictorNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<DecTreePredictorNodeModel> createNodeView(
            final int viewIndex, final DecTreePredictorNodeModel nodeModel) {
        if (viewIndex == 0) {
            return new DecTreePredictorGraphView(nodeModel);
        } else {
            return new DecTreePredictorNodeView(nodeModel);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new DefaultNodeSettingsPane() {
            {
                addDialogComponent(new DialogComponentNumber(
                   DecTreePredictorNodeModel.createMaxNumPatternSettings(),
                   /* label: */"Maximum number of stored patterns "
                                + "for HiLite-ing: ", 100));
            }
            private final SettingsModelString m_predSettings = PredictorHelper.getInstance().createPredictionColumn();
            private final SettingsModelBoolean m_override = PredictorHelper.getInstance().createChangePrediction();
            private DataColumnSpec m_lastTrainingColumn;
            {
                PredictorHelper.getInstance().addPredictionColumn(this, m_predSettings, m_override);
                addDialogComponent(new DialogComponentBoolean(
                        new SettingsModelBoolean(
                             DecTreePredictorNodeModel.SHOW_DISTRIBUTION,
                             false),
                     "Append columns with normalized class distribution"));
                PredictorHelper.getInstance().addProbabilitySuffix(this);
                m_override.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(final ChangeEvent e) {
                        if (!m_override.getBooleanValue()) {
                            final String predictionDefault =
                                PredictorHelper.getInstance().computePredictionDefault(m_lastTrainingColumn.getName());
                            m_predSettings.setStringValue(predictionDefault);
                        }
                    }

                });
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void onOpen() {
                super.onOpen();
                boolean origEnabled = m_override.getBooleanValue();
                m_predSettings.setEnabled(!origEnabled);
                m_predSettings.setEnabled(origEnabled);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void loadAdditionalSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
                throws NotConfigurableException {
                super.loadAdditionalSettingsFrom(settings, specs);
                m_lastTrainingColumn = ((PMMLPortObjectSpec)specs[0]).getTargetCols().iterator().next();
                boolean b = m_override.getBooleanValue();
                m_override.setBooleanValue(!b);
                m_override.setBooleanValue(b);
            }
        };
    }
}