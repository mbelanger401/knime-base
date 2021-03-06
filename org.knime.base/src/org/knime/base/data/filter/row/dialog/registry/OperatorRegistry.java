/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 */

package org.knime.base.data.filter.row.dialog.registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.knime.base.data.filter.row.dialog.OperatorFunction;
import org.knime.base.data.filter.row.dialog.OperatorPanel;
import org.knime.base.data.filter.row.dialog.OperatorValidation;
import org.knime.base.data.filter.row.dialog.model.Operator;
import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;

/**
 * Registry interface for {@link Operator}s.
 *
 * @param <F> the {@link OperatorFunction} subclass to apply on the operator
 * @author Viktor Buria
 * @since 3.8
 */
public interface OperatorRegistry<F extends OperatorFunction<?, ?>> {

    /**
     * Registers an operator's function.
     *
     * @param key the {@link OperatorKey}
     * @param value the operator's {@link Function}
     */
    void addOperator(OperatorKey key, OperatorValue<F> value);

    /**
     * Adds a function for the operator and any {@link DataType}.
     *
     * @param key the {@link Operator}
     * @param value the operator's {@link Function}
     */
    default void addDefaultOperator(final Operator key, final OperatorValue<F> value) {
        addOperator(OperatorKey.defaultKey(key), value);
    }

    /**
     * Adds a function for the operator and boolean {@link DataType}.
     *
     * @param key the {@link Operator}
     * @param value the operator's boolean {@link Function}
     */
    default void addBooleanOperator(final Operator key, final OperatorValue<F> value) {
        addOperator(OperatorKey.key(BooleanCell.TYPE, key), value);
    }

    /**
     * Adds a function for the operator and numeric {@link DataType}s.
     *
     * @param key the {@link Operator}
     * @param value the operator's numeric {@link Function}
     */
    default void addNumericOperator(final Operator key, final OperatorValue<F> value) {
        addOperator(OperatorKey.key(IntCell.TYPE, key), value);
        addOperator(OperatorKey.key(LongCell.TYPE, key), value);
        addOperator(OperatorKey.key(DoubleCell.TYPE, key), value);
    }

    /**
     * Adds a function for the operator and string {@link DataType}s.
     *
     * @param key the {@link Operator}
     * @param value the operator's sting {@link Function}
     */
    default void addStringOperator(final Operator key, final OperatorValue<F> value) {
        addOperator(OperatorKey.key(StringCell.TYPE, key), value);
    }

    /**
     * Finds a function for the given operator key.
     *
     * @param key the {@link OperatorKey}
     * @return the operator's {@link Function}
     */
    Optional<F> findFunction(OperatorKey key);

    /**
     * Finds a validation function for the given operator key.
     *
     * @param key the {@link OperatorKey}
     * @return the operator's {@linkplain OperatorValidation validation function}
     */
    Optional<OperatorValidation> findValidation(OperatorKey key);

    /**
     * Finds an operator's UI panel.
     *
     * @param key the {@link OperatorKey}
     * @return the operator's {@linkplain OperatorPanel UI panel}
     */
    Optional<OperatorPanel> findPanel(OperatorKey key);

    /**
     * Finds registered operators by {@link DataType}.
     *
     * @param dataType the {@link DataType}
     * @return the {@link List} of {@link Operator}
     */
    List<Operator> findRegisteredOperators(DataType dataType);

}
