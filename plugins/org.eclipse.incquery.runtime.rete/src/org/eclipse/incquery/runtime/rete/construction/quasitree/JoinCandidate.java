/*******************************************************************************
 * Copyright (c) 2004-2010 Gabor Bergmann and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gabor Bergmann - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.runtime.rete.construction.quasitree;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.incquery.runtime.base.api.FunctionalDependencyHelper;
import org.eclipse.incquery.runtime.matchers.planning.SubPlan;
import org.eclipse.incquery.runtime.matchers.psystem.PConstraint;
import org.eclipse.incquery.runtime.matchers.psystem.PVariable;
import org.eclipse.incquery.runtime.rete.collections.CollectionsFactory;

/**
 * @author Gabor Bergmann
 * 
 */
class JoinCandidate {
    SubPlan primary;
    SubPlan secondary;

    Set<PVariable> varPrimary;
    Set<PVariable> varSecondary;
    Set<PVariable> varCommon;

    JoinCandidate(SubPlan primary, SubPlan secondary) {
        super();
        this.primary = primary;
        this.secondary = secondary;

        varPrimary = getPrimary().getVariablesSet();
        varSecondary = getSecondary().getVariablesSet();
        varCommon = CollectionsFactory.getSet(varPrimary);
        varCommon.retainAll(varSecondary);
    }

    /**
     * @return the a
     */
    public SubPlan getPrimary() {
        return primary;
    }

    /**
     * @return the b
     */
    public SubPlan getSecondary() {
        return secondary;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return primary.toString() + " |x| " + secondary.toString();
    }

    /**
     * @return the varPrimary
     */
    public Set<PVariable> getVarPrimary() {
        return varPrimary;
    }

    /**
     * @return the varSecondary
     */
    public Set<PVariable> getVarSecondary() {
        return varSecondary;
    }

    public boolean isTrivial() {
        return getPrimary().equals(getSecondary());
    }

    public boolean isCheckOnly() {
        return varPrimary.containsAll(varSecondary) || varSecondary.containsAll(varPrimary);
    }

    public boolean isDescartes() {
        return Collections.disjoint(varPrimary, varSecondary);
    }

    private Boolean heath;

    // it is a Heath-join iff common variables functionally determine either all primary or all secondary variables
    public boolean isHeath() {
        if (heath == null) {
            Map<Set<PVariable>, Set<PVariable>> dependencies = new HashMap<Set<PVariable>, Set<PVariable>>();
            for (PConstraint pConstraint : primary.getAllEnforcedConstraints())
                dependencies.putAll(pConstraint.getFunctionalDependencies());
            for (PConstraint pConstraint : secondary.getAllEnforcedConstraints())
                dependencies.putAll(pConstraint.getFunctionalDependencies());

            // does varCommon determine either varPrimary or varSecondary?
            Set<PVariable> varCommonClosure = FunctionalDependencyHelper.closureOf(varCommon, dependencies);

            heath = varCommonClosure.containsAll(varPrimary) || varCommonClosure.containsAll(varSecondary);
        }
        return heath;
    }

}
