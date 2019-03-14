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
 *   14.03.2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shapley;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.knime.core.data.RowKey;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
class ShapleyValuesKeyGeneratorFactory <K> implements KeyGeneratorFactory<K, SVId> {

    private static final String DELIMITER = "_";

    private static final String FOI_REPLACED = "t";

    private static final String FOI_INTACT = "f";

    private final Function<String, K> m_stringToKey;

    private final Function<K, String> m_keyToString;

    /**
     *
     */
    public ShapleyValuesKeyGeneratorFactory(final Function<String, K> stringToKey, final Function<K, String> keyToString) {
        m_stringToKey = stringToKey;
        m_keyToString = keyToString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RowKeyGenerator<K, SVId> createGenerator(final K originalKey) {
        return new SVRowKeyGen(originalKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RowKeyChecker<K, SVId> createChecker(final K firstKeyInBatch) {
        return new SVRowKeyChecker(firstKeyInBatch);
    }

    private class SVRowKeyGen implements RowKeyGenerator<K, SVId> {

        private String m_originalKey;

        private SVRowKeyGen(final K originalKey) {
            m_originalKey = m_keyToString.apply(originalKey);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public K create(final SVId identificator) {
            StringBuilder sb = new StringBuilder(m_originalKey);
            sb.append(DELIMITER);
            sb.append(identificator.getFeatureIdx());
            sb.append(DELIMITER);
            sb.append(identificator.getIteration());
            sb.append(DELIMITER);
            sb.append(identificator.getFoiIntact() ? FOI_INTACT : FOI_REPLACED);
            return m_stringToKey.apply(sb.toString());
        }

    }

    private class SVRowKeyChecker implements RowKeyChecker<K, SVId> {

        /**
         * The number of components that the row key generator adds to the original row key
         */
        private static final int NUM_ADDITIONAL_COMPONENTS = 3;

        /**
         * Distance of the iteration component from the end of a generated row key
         */
        private static final int POS_ITERATION = 2;

        /**
         * Minimal number of components of a generated row key
         */
        private static final int MIN_COMPONENTS_PER_KEY = 4;

        /**
         * Distance of the feature index component from the end of the split row key
         */
        private static final int POS_FEATURE_IDX = 3;

        private final String m_originalKey;

        /**
         * Checks if a row belongs to the current batch. </br>
         * Has to be initialized with the {@link RowKey} of the first row in the batch. Fails if the first row key does
         * not designate the start of a row batch.
         *
         * @param keyOfFirstRowInBatch the row key of the first row in the current row batch
         */
        public SVRowKeyChecker(final K keyOfFirstRowInBatch) {
            final K key = keyOfFirstRowInBatch;
            final String[] split = split(key);
            int featureIdx = -1;
            int iteration = -1;
            try {
                featureIdx = parseFeatureIdx(split);
                iteration = parseIteration(split);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(createErrorString(key));
            }
            CheckUtils.checkArgument(featureIdx == 0 && iteration == 0,
                "The row with key '" + key + "' is not the first row in a row batch.");
            m_originalKey = recreateOriginalKey(split);
        }

        @Override
        public String getOriginalKey() {
            return m_originalKey;
        }

        private String createErrorString(final K generatedKey) {
            return "The row key '" + generatedKey + "' was not created by an instance of this RowKeyGenerator.";
        }

        /**
         * @param split
         * @return
         */
        private int parseIteration(final String[] split) {
            return Integer.parseInt(split[split.length - POS_ITERATION]);
        }

        private int parseFeatureIdx(final String[] split) {
            return Integer.parseInt(split[split.length - POS_FEATURE_IDX]);
        }

        private String[] split(final K key) {
            final String[] split = m_keyToString.apply(key).split(DELIMITER);
            CheckUtils.checkArgument(split.length >= MIN_COMPONENTS_PER_KEY, createErrorString(key));
            return split;
        }

        private String recreateOriginalKey(final String[] split) {
            // TODO figure out if there is a more efficient way to do this
            return Arrays.stream(split, 0, split.length - NUM_ADDITIONAL_COMPONENTS)
                .collect(Collectors.joining(DELIMITER));
        }

        private boolean isFoiIntact(final String[] split) {
            return split[split.length - 1].equals(FOI_INTACT);
        }

        private boolean isRightOrder(final SVId expectedId, final String[] split) {
            return expectedId.getFeatureIdx() == parseFeatureIdx(split)
                && expectedId.getIteration() == parseIteration(split)
                && isFoiIntact(split) == expectedId.getFoiIntact();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean check(final K key, final SVId expectedId) {
            final String[] split = split(key);
            final String originalKey = recreateOriginalKey(split);
            CheckUtils.checkArgument(m_originalKey.equals(originalKey),
                "The row with key '" + key + "' does not belong to the current batch of rows.");
            CheckUtils.checkArgument(isRightOrder(expectedId, split), "The rows corresponding to the original row key '"
                + m_originalKey + "' are not in the expected order.");
            return true;
        }

    }

}
